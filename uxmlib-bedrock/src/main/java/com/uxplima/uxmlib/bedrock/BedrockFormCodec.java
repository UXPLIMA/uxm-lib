package com.uxplima.uxmlib.bedrock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Turns a {@link BedrockFormRequest} and a {@link BedrockFormResponse} into bytes and back, for a plugin that carries
 * forms between servers, such as from a backend to a proxy that runs Geyser.
 *
 * <p>The bytes start with a format version and carry no request id: the plugin's own frame carries that. Reading
 * rejects anything it did not write: an unknown version or tag, more than {@value #MAX_ENTRIES} buttons, widgets,
 * options or values, a string longer than {@value #MAX_TEXT} characters, or trailing bytes. A rejection is an
 * {@link IllegalArgumentException} whose message names what was wrong and never the content, which may be a secret.
 */
public final class BedrockFormCodec {

    /** The most buttons, widgets, dropdown options or submitted values one form carries. */
    public static final int MAX_ENTRIES = 256;

    /** The longest string one field carries, in characters. */
    public static final int MAX_TEXT = 4096;

    private static final byte VERSION = 1;

    private static final byte SIMPLE = 1;
    private static final byte MODAL = 2;
    private static final byte CUSTOM = 3;

    private static final byte TAPPED = 1;
    private static final byte SUBMITTED = 2;
    private static final byte CLOSED = 3;

    private static final byte LABEL = 1;
    private static final byte INPUT = 2;
    private static final byte DROPDOWN = 3;
    private static final byte SLIDER = 4;
    private static final byte TOGGLE = 5;

    private BedrockFormCodec() {}

    /**
     * The bytes of {@code request}.
     *
     * @throws IllegalArgumentException when the request is over a limit this codec reads back
     */
    public static byte[] encode(BedrockFormRequest request) {
        Objects.requireNonNull(request, "request");
        return write(out -> {
            switch (request) {
                case BedrockFormRequest.Simple simple -> {
                    out.writeByte(SIMPLE);
                    text(out, simple.title());
                    optional(out, simple.content());
                    count(out, simple.buttons().size());
                    for (BedrockButton button : simple.buttons()) {
                        text(out, button.text());
                        BedrockImage image = button.image();
                        out.writeBoolean(image != null);
                        if (image != null) {
                            out.writeBoolean(image.kind() == BedrockImage.Kind.PATH);
                            text(out, image.value());
                        }
                    }
                }
                case BedrockFormRequest.Modal modal -> {
                    out.writeByte(MODAL);
                    text(out, modal.title());
                    optional(out, modal.content());
                    text(out, modal.button1());
                    text(out, modal.button2());
                }
                case BedrockFormRequest.Custom custom -> {
                    out.writeByte(CUSTOM);
                    text(out, custom.title());
                    optional(out, custom.content());
                    count(out, custom.widgets().size());
                    for (BedrockWidget widget : custom.widgets()) {
                        widget(out, widget);
                    }
                }
            }
        });
    }

    /**
     * The bytes of {@code response}.
     *
     * @throws IllegalArgumentException when the response is over a limit this codec reads back
     */
    public static byte[] encode(BedrockFormResponse response) {
        Objects.requireNonNull(response, "response");
        return write(out -> {
            switch (response) {
                case BedrockFormResponse.Tapped tapped -> {
                    out.writeByte(TAPPED);
                    out.writeInt(tapped.index());
                }
                case BedrockFormResponse.Submitted submitted -> {
                    out.writeByte(SUBMITTED);
                    count(out, submitted.values().size());
                    for (Map.Entry<String, String> value : submitted.values().entrySet()) {
                        text(out, value.getKey());
                        text(out, value.getValue());
                    }
                }
                case BedrockFormResponse.Closed closed -> out.writeByte(CLOSED);
            }
        });
    }

    /**
     * The request {@code bytes} carry.
     *
     * @throws IllegalArgumentException when the bytes are not a request this codec wrote
     */
    public static BedrockFormRequest decodeRequest(byte[] bytes) {
        return read(bytes, in -> {
            byte kind = in.readByte();
            return switch (kind) {
                case SIMPLE -> {
                    String title = text(in);
                    String content = optional(in);
                    int count = count(in);
                    List<BedrockButton> buttons = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String label = text(in);
                        BedrockImage image = null;
                        if (in.readBoolean()) {
                            BedrockImage.Kind imageKind =
                                    in.readBoolean() ? BedrockImage.Kind.PATH : BedrockImage.Kind.URL;
                            image = new BedrockImage(imageKind, text(in));
                        }
                        buttons.add(new BedrockButton(label, image));
                    }
                    yield new BedrockFormRequest.Simple(title, content, buttons);
                }
                case MODAL -> new BedrockFormRequest.Modal(text(in), optional(in), text(in), text(in));
                case CUSTOM -> {
                    String title = text(in);
                    String content = optional(in);
                    int count = count(in);
                    List<BedrockWidget> widgets = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        widgets.add(widget(in));
                    }
                    yield new BedrockFormRequest.Custom(title, content, widgets);
                }
                default -> throw new IllegalArgumentException("unknown bedrock form kind " + kind);
            };
        });
    }

    /**
     * The response {@code bytes} carry.
     *
     * @throws IllegalArgumentException when the bytes are not a response this codec wrote
     */
    public static BedrockFormResponse decodeResponse(byte[] bytes) {
        return read(bytes, in -> {
            byte kind = in.readByte();
            return switch (kind) {
                case TAPPED -> new BedrockFormResponse.Tapped(in.readInt());
                case SUBMITTED -> {
                    int count = count(in);
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int i = 0; i < count; i++) {
                        String name = text(in);
                        if (values.put(name, text(in)) != null) {
                            throw new IllegalArgumentException("bedrock form response repeats a field");
                        }
                    }
                    yield new BedrockFormResponse.Submitted(values);
                }
                case CLOSED -> BedrockFormResponse.CLOSED;
                default -> throw new IllegalArgumentException("unknown bedrock form response kind " + kind);
            };
        });
    }

    private static void widget(DataOutputStream out, BedrockWidget widget) throws IOException {
        switch (widget) {
            case BedrockWidget.Label label -> {
                out.writeByte(LABEL);
                text(out, label.text());
            }
            case BedrockWidget.Input input -> {
                out.writeByte(INPUT);
                text(out, input.name());
                text(out, input.label());
                text(out, input.placeholder());
                text(out, input.defaultText());
            }
            case BedrockWidget.Dropdown dropdown -> {
                out.writeByte(DROPDOWN);
                text(out, dropdown.name());
                text(out, dropdown.label());
                count(out, dropdown.options().size());
                for (String option : dropdown.options()) {
                    text(out, option);
                }
                out.writeInt(dropdown.defaultIndex());
            }
            case BedrockWidget.Slider slider -> {
                out.writeByte(SLIDER);
                text(out, slider.name());
                text(out, slider.label());
                out.writeInt(slider.min());
                out.writeInt(slider.max());
                out.writeInt(slider.step());
                out.writeInt(slider.defaultValue());
            }
            case BedrockWidget.Toggle toggle -> {
                out.writeByte(TOGGLE);
                text(out, toggle.name());
                text(out, toggle.label());
                out.writeBoolean(toggle.defaultValue());
            }
        }
    }

    private static BedrockWidget widget(DataInputStream in) throws IOException {
        byte kind = in.readByte();
        return switch (kind) {
            case LABEL -> new BedrockWidget.Label(text(in));
            case INPUT -> new BedrockWidget.Input(text(in), text(in), text(in), text(in));
            case DROPDOWN -> {
                String name = text(in);
                String label = text(in);
                int count = count(in);
                List<String> options = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    options.add(text(in));
                }
                yield new BedrockWidget.Dropdown(name, label, options, in.readInt());
            }
            case SLIDER ->
                new BedrockWidget.Slider(text(in), text(in), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            case TOGGLE -> new BedrockWidget.Toggle(text(in), text(in), in.readBoolean());
            default -> throw new IllegalArgumentException("unknown bedrock widget kind " + kind);
        };
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        if (value.length() > MAX_TEXT) {
            throw new IllegalArgumentException("bedrock form text over " + MAX_TEXT + " characters");
        }
        out.writeUTF(value);
    }

    private static void optional(DataOutputStream out, @Nullable String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            text(out, value);
        }
    }

    private static void count(DataOutputStream out, int count) throws IOException {
        if (count > MAX_ENTRIES) {
            throw new IllegalArgumentException("bedrock form over " + MAX_ENTRIES + " entries");
        }
        out.writeShort(count);
    }

    private static String text(DataInputStream in) throws IOException {
        String value = in.readUTF();
        if (value.length() > MAX_TEXT) {
            throw new IllegalArgumentException("bedrock form text over " + MAX_TEXT + " characters");
        }
        return value;
    }

    private static @Nullable String optional(DataInputStream in) throws IOException {
        return in.readBoolean() ? text(in) : null;
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        if (count > MAX_ENTRIES) {
            throw new IllegalArgumentException("bedrock form over " + MAX_ENTRIES + " entries");
        }
        return count;
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }

    private static byte[] write(Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            writer.write(out);
        } catch (IOException tooLong) {
            // Only writeUTF can fail on a byte array, for a string over 65535 encoded bytes.
            throw new IllegalArgumentException("bedrock form text too long to encode", tooLong);
        }
        return bytes.toByteArray();
    }

    private static <T> T read(byte[] bytes, Reader<T> reader) {
        Objects.requireNonNull(bytes, "bytes");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unknown bedrock form format " + version);
            }
            T value = reader.read(in);
            if (in.available() > 0) {
                throw new IllegalArgumentException("bedrock form bytes run past the form");
            }
            return value;
        } catch (IOException truncated) {
            throw new IllegalArgumentException("bedrock form bytes end early", truncated);
        }
    }
}
