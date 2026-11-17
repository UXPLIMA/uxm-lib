package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** A secret is never asked for in chat or on a sign; any other line opens in the mode the operator chose. */
class TextInputModeTest {

    @Test
    void aSecretGoesToTheDialogWhenOneIsWiredElseTheAnvil() {
        assertThat(TextInput.modeFor(InputMode.CHAT, true, true)).isEqualTo(InputMode.DIALOG);
        assertThat(TextInput.modeFor(InputMode.SIGN, true, true)).isEqualTo(InputMode.DIALOG);
        assertThat(TextInput.modeFor(InputMode.CHAT, true, false)).isEqualTo(InputMode.ANVIL);
        assertThat(TextInput.modeFor(InputMode.SIGN, true, false)).isEqualTo(InputMode.ANVIL);
        assertThat(TextInput.modeFor(InputMode.ANVIL, true, true)).isEqualTo(InputMode.ANVIL);
        assertThat(TextInput.modeFor(InputMode.DIALOG, true, true)).isEqualTo(InputMode.DIALOG);
        // A dialog configured where none is wired falls back to the sign for other lines; a secret takes the anvil.
        assertThat(TextInput.modeFor(InputMode.DIALOG, true, false)).isEqualTo(InputMode.ANVIL);
    }

    @Test
    void anyOtherLineKeepsTheOperatorsMode() {
        for (InputMode mode : InputMode.values()) {
            assertThat(TextInput.modeFor(mode, false, true)).isEqualTo(mode);
        }
    }

    @Test
    void aRequestIsASecretOnlyWhenMarkedSo() {
        InputRequest pin = InputRequest.of("security.pin", "security.pin-prompt");

        assertThat(pin.secret()).isFalse();
        assertThat(pin.asSecret().secret()).isTrue();
        assertThat(new InputRequest("k", "l", Map.of(), null).secret()).isFalse();
    }
}
