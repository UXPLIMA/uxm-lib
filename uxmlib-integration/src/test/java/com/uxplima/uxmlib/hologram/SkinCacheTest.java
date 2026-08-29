package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Pure tests of the TTL/size cache behind the skin resolver: clock is injected, no real time passes. */
class SkinCacheTest {

    @Test
    void storesAndReadsBackAValue() {
        SkinCache cache = new SkinCache(8, Duration.ofMinutes(1), System::nanoTime);
        cache.put("Notch", "base64-blob");
        assertThat(cache.get("Notch")).contains("base64-blob");
    }

    @Test
    void missesForAnUnknownKey() {
        SkinCache cache = new SkinCache(8, Duration.ofMinutes(1), System::nanoTime);
        assertThat(cache.get("Nobody")).isEmpty();
    }

    @Test
    void expiresAnEntryOnceTheTtlElapses() {
        AtomicLong now = new AtomicLong(0);
        SkinCache cache = new SkinCache(8, Duration.ofSeconds(10), now::get);
        cache.put("Notch", "blob");
        now.set(Duration.ofSeconds(9).toNanos());
        assertThat(cache.get("Notch")).contains("blob");
        now.set(Duration.ofSeconds(11).toNanos());
        assertThat(cache.get("Notch")).isEmpty();
    }

    @Test
    void evictsTheOldestWhenOverCapacity() {
        AtomicLong now = new AtomicLong(0);
        SkinCache cache = new SkinCache(2, Duration.ofHours(1), now::get);
        now.set(1);
        cache.put("a", "1");
        now.set(2);
        cache.put("b", "2");
        now.set(3);
        cache.put("c", "3"); // pushes out the oldest, "a"
        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).contains("2");
        assertThat(cache.get("c")).contains("3");
    }

    @Test
    void cachesAnegativeLookupSoItIsNotRetriedEveryCall() {
        SkinCache cache = new SkinCache(8, Duration.ofMinutes(1), System::nanoTime);
        cache.putAbsent("Ghost");
        Optional<Optional<String>> hit = cache.lookup("Ghost");
        assertThat(hit).isPresent();
        assertThat(hit.get()).isEmpty();
    }

    @Test
    void lookupDistinguishesAbsentFromUncached() {
        SkinCache cache = new SkinCache(8, Duration.ofMinutes(1), System::nanoTime);
        assertThat(cache.lookup("Never")).isEmpty(); // not cached at all
        cache.putAbsent("Ghost");
        assertThat(cache.lookup("Ghost")).isPresent(); // cached as a known-absent
    }

    @Test
    void rejectsANonPositiveCapacity() {
        assertThatThrownBy(() -> new SkinCache(0, Duration.ofMinutes(1), System::nanoTime))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThatThrownBy(() -> new SkinCache(8, Duration.ZERO, System::nanoTime))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
