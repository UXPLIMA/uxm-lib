package com.uxplima.uxmlib.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Geyser's platform and input names map to the same devices Floodgate's do. */
class GeyserClientTest {

    @Test
    void phonesAreMobileAndATelevisionIsNot() {
        assertThat(GeyserBedrockDetector.device("GOOGLE").mobile()).isTrue();
        assertThat(GeyserBedrockDetector.device("IOS").mobile()).isTrue();
        assertThat(GeyserBedrockDetector.device("TVOS").mobile()).isFalse();
        assertThat(GeyserBedrockDetector.device("WIN32")).isEqualTo(BedrockClient.Device.WINDOWS);
        assertThat(GeyserBedrockDetector.device("")).isEqualTo(BedrockClient.Device.UNKNOWN);
        assertThat(GeyserBedrockDetector.input("TOUCH")).isEqualTo(BedrockClient.Input.TOUCH);
        assertThat(GeyserBedrockDetector.input("SOMETHING_NEW")).isEqualTo(BedrockClient.Input.UNKNOWN);
    }
}
