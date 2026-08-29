package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullUnmarked
class ClientCapabilitiesTest {

    /** A modern Java client: not bedrock, current protocol (sentinel). */
    private static final ClientProfile JAVA_MODERN = ClientProfile.javaModern();

    @Test
    void java_modern_supports_everything() {
        ClientCapabilities caps = ClientCapabilities.of(JAVA_MODERN);
        assertThat(caps.gradient()).isTrue();
        assertThat(caps.customFont()).isTrue();
        assertThat(caps.hoverItem()).isTrue();
    }

    @Test
    void bedrock_loses_gradient_and_custom_font() {
        ClientCapabilities caps = ClientCapabilities.of(new ClientProfile(true, ClientProfile.CURRENT_PROTOCOL));
        assertThat(caps.gradient()).isFalse();
        assertThat(caps.customFont()).isFalse();
    }

    @Test
    void profile_exposes_the_capability_flags_directly() {
        ClientProfile bedrock = new ClientProfile(true, ClientProfile.CURRENT_PROTOCOL);
        assertThat(bedrock.supportsGradient()).isFalse();
        assertThat(bedrock.supportsCustomFont()).isFalse();
        ClientProfile java = new ClientProfile(false, ClientProfile.CURRENT_PROTOCOL);
        assertThat(java.supportsGradient()).isTrue();
        assertThat(java.supportsCustomFont()).isTrue();
    }

    @Test
    void hover_item_gated_below_threshold() {
        // A profile below the hover-item protocol threshold cannot render show_item.
        ClientProfile old = new ClientProfile(false, ClientCapabilities.MIN_PROTOCOL_FOR_HOVER_ITEM - 1);
        assertThat(ClientCapabilities.of(old).hoverItem()).isFalse();
        assertThat(old.supportsHoverItem()).isFalse();
    }

    @Test
    void hover_item_allowed_at_threshold() {
        ClientProfile atThreshold = new ClientProfile(false, ClientCapabilities.MIN_PROTOCOL_FOR_HOVER_ITEM);
        assertThat(ClientCapabilities.of(atThreshold).hoverItem()).isTrue();
        assertThat(atThreshold.supportsHoverItem()).isTrue();
    }

    @Test
    void current_protocol_sentinel_is_treated_as_above_every_threshold() {
        // CURRENT_PROTOCOL means "detection absent / assume modern"; it must
        // never be gated below a finite threshold.
        ClientProfile current = new ClientProfile(false, ClientProfile.CURRENT_PROTOCOL);
        assertThat(current.supportsHoverItem()).isTrue();
        assertThat(ClientCapabilities.of(current).hoverItem()).isTrue();
    }

    @Test
    void bedrock_still_gets_hover_item_when_protocol_allows() {
        // Bedrock degrades gradient/font but hover-item is protocol-gated, not
        // bedrock-gated: Geyser translates show_item fine on a modern protocol.
        ClientProfile bedrock = new ClientProfile(true, ClientProfile.CURRENT_PROTOCOL);
        assertThat(ClientCapabilities.of(bedrock).hoverItem()).isTrue();
    }

    @Test
    void configurable_threshold_gates_hover_item() {
        // A real protocol below the operator-set threshold loses hover-item.
        ClientProfile old = new ClientProfile(false, 46);
        assertThat(ClientCapabilities.of(old, 47).hoverItem()).isFalse();
        assertThat(ClientCapabilities.of(old, 46).hoverItem()).isTrue();
    }

    @Test
    void configurable_threshold_does_not_gate_current_sentinel() {
        ClientProfile current = new ClientProfile(false, ClientProfile.CURRENT_PROTOCOL);
        assertThat(ClientCapabilities.of(current, 999_999).hoverItem()).isTrue();
    }

    @Test
    void rejects_null_profile() {
        assertThatNullPointerException().isThrownBy(() -> ClientCapabilities.of(null));
        assertThatNullPointerException().isThrownBy(() -> ClientCapabilities.of(null, 0));
    }
}
