package com.uxplima.uxmlib.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Covers the write-behind / dirty-coalescing path over a counting in-memory backend. */
class WriteBehindStorageTest {

    record User(String id, int score) {}

    /** A counting in-memory StorageProvider so the test can see exactly when the backend is hit. */
    static final class FakeBackend implements StorageProvider<String, User> {
        private final Map<String, User> store = new LinkedHashMap<>();
        int reads;
        int writes;

        @Override
        public Optional<User> findById(String id) {
            reads++;
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void save(User entity) {
            writes++;
            store.put(entity.id(), entity);
        }

        @Override
        public boolean deleteById(String id) {
            return store.remove(id) != null;
        }
    }

    @Test
    void writeMarksDirtyWithoutTouchingTheBackend() {
        FakeBackend backend = new FakeBackend();
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();

        storage.save(new User("u1", 10));

        assertThat(backend.writes).isZero(); // no immediate provider write
        assertThat(storage.dirtyCount()).isEqualTo(1);
        assertThat(storage.cached("u1")).get().extracting(User::score).isEqualTo(10); // cache updated
    }

    @Test
    void twoWritesToOneKeyThenFlushCollapseToOneProviderSave() {
        FakeBackend backend = new FakeBackend();
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();

        storage.save(new User("u1", 1));
        storage.save(new User("u1", 2)); // overwrites the pending value

        storage.flushAll();

        assertThat(backend.writes).isEqualTo(1); // coalesced into a single save
        assertThat(backend.findById("u1")).get().extracting(User::score).isEqualTo(2); // last write wins
    }

    @Test
    void flushClearsTheDirtySetAndASecondFlushIsANoOp() {
        FakeBackend backend = new FakeBackend();
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();
        storage.save(new User("a", 1));
        storage.save(new User("b", 2));

        storage.flushAll();
        assertThat(storage.dirtyCount()).isZero();
        assertThat(backend.writes).isEqualTo(2);

        storage.flushAll(); // nothing dirty
        assertThat(backend.writes).isEqualTo(2);
    }

    @Test
    void flushKeyWritesOnlyThatKey() {
        FakeBackend backend = new FakeBackend();
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();
        storage.save(new User("a", 1));
        storage.save(new User("b", 2));

        boolean flushed = storage.flush("a");

        assertThat(flushed).isTrue();
        assertThat(backend.writes).isEqualTo(1);
        assertThat(storage.dirtyCount()).isEqualTo(1); // b still dirty
        assertThat(storage.flush("a")).isFalse(); // already clean
    }

    @Test
    void getReadsThroughOnAMissAndCachesTheResult() {
        FakeBackend backend = new FakeBackend();
        backend.save(new User("u1", 5));
        backend.writes = 0;
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();

        assertThat(storage.get("u1")).get().extracting(User::score).isEqualTo(5);
        assertThat(storage.get("u1")).isPresent(); // second read served from cache
        assertThat(backend.reads).isEqualTo(1);
        assertThat(storage.dirtyCount()).isZero(); // a read never marks dirty
    }

    @Test
    void getReturnsThePendingDirtyValueOverTheBackend() {
        FakeBackend backend = new FakeBackend();
        backend.save(new User("u1", 5));
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();

        storage.save(new User("u1", 99)); // dirty, not yet flushed
        assertThat(storage.get("u1")).get().extracting(User::score).isEqualTo(99);
    }

    @Test
    void evictedButDirtyValueStillFlushesToTheBackend() {
        FakeBackend backend = new FakeBackend();
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).maximumSize(1).build();

        storage.save(new User("a", 1));
        storage.save(new User("b", 2)); // may evict "a" from the read tier under maximumSize(1)

        storage.flushAll();
        assertThat(backend.writes).isEqualTo(2); // a pending write is never lost to eviction
        assertThat(backend.findById("a")).get().extracting(User::score).isEqualTo(1);
        assertThat(backend.findById("b")).get().extracting(User::score).isEqualTo(2);
    }

    @Test
    void invalidateDropsACleanEntryButNeverADirtyOne() {
        FakeBackend backend = new FakeBackend();
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();
        storage.save(new User("a", 1));

        storage.invalidate("a"); // a is dirty, so it must stay pending
        assertThat(storage.dirtyCount()).isEqualTo(1);

        storage.flushAll();
        storage.invalidate("a"); // now clean, drop it
        assertThat(storage.cached("a")).isEmpty();
    }

    /** A backend whose first {@code save} throws, then succeeds: models a transient DB hiccup. */
    static final class FlakyBackend implements StorageProvider<String, User> {
        private final Map<String, User> store = new LinkedHashMap<>();
        boolean failNextSave;
        int writes;

        @Override
        public Optional<User> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void save(User entity) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("transient backend failure");
            }
            writes++;
            store.put(entity.id(), entity);
        }

        @Override
        public boolean deleteById(String id) {
            return store.remove(id) != null;
        }
    }

    @Test
    void flushKeepsTheStagedWriteDirtyWhenTheBackendThrows() {
        FlakyBackend backend = new FlakyBackend();
        backend.failNextSave = true;
        WriteBehindStorage<String, User> storage =
                WriteBehindStorage.builder(backend, User::id).build();
        storage.save(new User("u1", 7));

        // The save throws, but the dirty write must survive for the next flush (durability of write-behind).
        assertThatThrownBy(() -> storage.flush("u1")).isInstanceOf(IllegalStateException.class);
        assertThat(storage.dirtyCount()).isEqualTo(1);
        assertThat(storage.cached("u1")).get().extracting(User::score).isEqualTo(7);

        // Retry now succeeds and clears the dirty flag.
        assertThat(storage.flush("u1")).isTrue();
        assertThat(storage.dirtyCount()).isZero();
        assertThat(backend.findById("u1")).get().extracting(User::score).isEqualTo(7);
    }
}
