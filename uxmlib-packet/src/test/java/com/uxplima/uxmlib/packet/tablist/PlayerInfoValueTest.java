package com.uxplima.uxmlib.packet.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Value and guard contract of one id-addressed batch update. */
class PlayerInfoValueTest {

    @Test
    void factoryCarriesIdAndValue() {
        UUID id = UUID.randomUUID();

        PlayerInfoValue<Integer> value = PlayerInfoValue.of(id, 42);

        assertThat(value.id()).isEqualTo(id);
        assertThat(value.value()).isEqualTo(42);
    }

    @Test
    void rejectsNullIdAndValue() {
        UUID id = UUID.randomUUID();

        assertThatNullPointerException().isThrownBy(() -> PlayerInfoValue.of(nullUuid(), 1));
        assertThatNullPointerException().isThrownBy(() -> PlayerInfoValue.of(id, nullString()));
    }

    @SuppressWarnings("NullAway")
    private static UUID nullUuid() {
        return null;
    }

    @SuppressWarnings("NullAway")
    private static String nullString() {
        return null;
    }
}
