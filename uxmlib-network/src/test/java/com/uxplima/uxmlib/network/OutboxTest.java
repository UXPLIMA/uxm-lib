package com.uxplima.uxmlib.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A full outbox drops its oldest frame, and only a frame it really dropped is counted. */
class OutboxTest {

    @Test
    void aFullBoxDropsTheOldestAndCountsIt() throws InterruptedException {
        Outbox box = new Outbox(2, 1_000);

        box.offer(new byte[] {1});
        box.offer(new byte[] {2});
        boolean dropped = box.offer(new byte[] {3});

        assertThat(dropped).isTrue();
        assertThat(box.dropped()).isEqualTo(1);
        assertThat(box.next(0)).containsExactly(2);
        assertThat(box.next(0)).containsExactly(3);
        assertThat(box.next(0)).isNull();
    }

    @Test
    void aBoxOverItsBytesDropsOnlyWhatMakesRoom() throws InterruptedException {
        Outbox box = new Outbox(10, 10);

        box.offer(new byte[6]);
        box.offer(new byte[3]);
        box.offer(new byte[4]);

        assertThat(box.dropped()).isEqualTo(1);
        assertThat(box.next(0)).hasSize(3);
        assertThat(box.next(0)).hasSize(4);
    }
}
