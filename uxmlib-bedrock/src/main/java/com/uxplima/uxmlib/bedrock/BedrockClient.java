package com.uxplima.uxmlib.bedrock;

import java.util.Locale;
import java.util.Objects;

/**
 * What a Bedrock player's client told the proxy when it connected: the device family, how the player steers, which
 * screen layout the client draws, the client version and its language.
 *
 * <p>The values decide how a plugin should talk to that player: a touch player cannot hover a lore line, a pocket
 * layout has no room for wide menu art, and the version tells which client features exist. They are fixed for the
 * session, because the client reports them once.
 *
 * @param device the device family; {@link Device#UNKNOWN} when the client did not say
 * @param input the input the player uses; {@link Input#UNKNOWN} when the client did not say
 * @param layout the screen layout the client draws
 * @param version the client version as it reported it, for example {@code "1.26.60"}; empty when unknown
 * @param language the client language tag in lower case, for example {@code "tr_tr"}; empty when unknown
 */
public record BedrockClient(Device device, Input input, Layout layout, String version, String language) {

    /** A client nothing is known about. */
    public static final BedrockClient UNKNOWN =
            new BedrockClient(Device.UNKNOWN, Input.UNKNOWN, Layout.CLASSIC, "", "");

    public BedrockClient {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(version, "version");
        language = Objects.requireNonNull(language, "language").toLowerCase(Locale.ROOT);
    }

    /** Whether the player steers by touching the screen. */
    public boolean touch() {
        return input == Input.TOUCH;
    }

    /** The device family a Bedrock client runs on. */
    public enum Device {
        ANDROID,
        IOS,
        MACOS,
        FIRE_OS,
        WINDOWS,
        PLAYSTATION,
        SWITCH,
        XBOX,
        VR,
        OTHER,
        UNKNOWN;

        /** Whether the device is a phone or a tablet. */
        public boolean mobile() {
            return this == ANDROID || this == IOS || this == FIRE_OS;
        }

        /** Whether the device is a game console. */
        public boolean console() {
            return this == PLAYSTATION || this == SWITCH || this == XBOX;
        }
    }

    /** How a Bedrock player steers. */
    public enum Input {
        KEYBOARD_MOUSE,
        TOUCH,
        CONTROLLER,
        VR,
        UNKNOWN
    }

    /** The screen layout a Bedrock client draws: the wide classic one or the compact pocket one. */
    public enum Layout {
        CLASSIC,
        POCKET
    }
}
