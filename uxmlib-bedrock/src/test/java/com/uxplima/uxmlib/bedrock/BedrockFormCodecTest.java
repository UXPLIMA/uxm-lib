package com.uxplima.uxmlib.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** A form and its answer cross a wire as bytes and come back the same, and bytes it did not write are refused. */
class BedrockFormCodecTest {

    @Test
    void everyFormKindComesBackTheSame() {
        List<BedrockFormRequest> requests = List.of(
                new BedrockFormRequest.Simple(
                        "Shop",
                        "Pick a category",
                        List.of(
                                new BedrockButton("Tools", new BedrockImage(BedrockImage.Kind.PATH, "textures/a")),
                                new BedrockButton("Heads", new BedrockImage(BedrockImage.Kind.URL, "https://x/y.png")),
                                new BedrockButton("Food", null))),
                new BedrockFormRequest.Simple("Empty", null, List.of()),
                new BedrockFormRequest.Modal("Sure?", null, "Yes", "No"),
                new BedrockFormRequest.Custom(
                        "Buy",
                        "Choose an offer",
                        List.of(
                                new BedrockWidget.Label("Prices include tax"),
                                new BedrockWidget.Input("note", "Note", "optional", "Şeker"),
                                new BedrockWidget.Dropdown("offer", "Offer", List.of("Sword", "Shield"), 1),
                                new BedrockWidget.Slider("amount", "Amount", 1, 64, 1, 16),
                                new BedrockWidget.Toggle("gift", "Gift", true))));

        for (BedrockFormRequest request : requests) {
            assertThat(BedrockFormCodec.decodeRequest(BedrockFormCodec.encode(request)))
                    .isEqualTo(request);
        }
    }

    @Test
    void everyAnswerComesBackTheSame() {
        List<BedrockFormResponse> responses = List.of(
                new BedrockFormResponse.Tapped(2),
                new BedrockFormResponse.Submitted(Map.of("pin", "4821", "gift", "true")),
                BedrockFormResponse.CLOSED);

        for (BedrockFormResponse response : responses) {
            assertThat(BedrockFormCodec.decodeResponse(BedrockFormCodec.encode(response)))
                    .isEqualTo(response);
        }
    }

    @Test
    void bytesItDidNotWriteAreRefused() {
        byte[] closed = BedrockFormCodec.encode(BedrockFormResponse.CLOSED);
        byte[] otherVersion = closed.clone();
        otherVersion[0] = 9;
        byte[] trailing = Arrays.copyOf(closed, closed.length + 1);
        byte[] request = BedrockFormCodec.encode(new BedrockFormRequest.Modal("Sure?", null, "Yes", "No"));
        byte[] truncated = Arrays.copyOf(request, request.length - 2);

        assertThatThrownBy(() -> BedrockFormCodec.decodeResponse(otherVersion))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BedrockFormCodec.decodeResponse(trailing))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BedrockFormCodec.decodeRequest(truncated))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BedrockFormCodec.decodeRequest(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BedrockFormCodec.decodeResponse(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFormOverTheLimitsIsRefusedOnBothSides() {
        List<BedrockButton> tooMany =
                Collections.nCopies(BedrockFormCodec.MAX_ENTRIES + 1, new BedrockButton("x", null));

        assertThatThrownBy(() -> BedrockFormCodec.encode(new BedrockFormRequest.Simple("Shop", null, tooMany)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BedrockFormCodec.encode(
                        new BedrockFormRequest.Modal("x".repeat(BedrockFormCodec.MAX_TEXT + 1), null, "a", "b")))
                .isInstanceOf(IllegalArgumentException.class);

        // A count past the limit, as a peer that is not this codec could write it.
        byte[] simple = BedrockFormCodec.encode(new BedrockFormRequest.Simple("Shop", null, List.of()));
        simple[simple.length - 2] = (byte) 0xFF;
        simple[simple.length - 1] = (byte) 0xFF;
        assertThatThrownBy(() -> BedrockFormCodec.decodeRequest(simple)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSubmittedSecretStaysOutOfTextAndErrors() {
        BedrockFormResponse.Submitted pin = new BedrockFormResponse.Submitted(Map.of("pin", "4821"));
        byte[] bytes = BedrockFormCodec.encode(pin);
        byte[] cut = Arrays.copyOf(bytes, bytes.length - 1);

        assertThat(pin.toString()).contains("pin").doesNotContain("4821");
        assertThatThrownBy(() -> BedrockFormCodec.decodeResponse(cut))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(
                        error -> assertThat(String.valueOf(error.getMessage())).doesNotContain("482"));
    }
}
