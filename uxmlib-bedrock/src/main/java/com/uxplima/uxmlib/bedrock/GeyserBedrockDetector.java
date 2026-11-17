package com.uxplima.uxmlib.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.geysermc.api.Geyser;
import org.geysermc.api.connection.Connection;
import org.jspecify.annotations.Nullable;

/**
 * The Geyser-backed {@link BedrockDetector}, for servers that run Geyser without Floodgate. Geyser alone still
 * knows which connections came from Bedrock, so those servers can have native forms too instead of falling back
 * to a chest menu for every Bedrock player.
 *
 * <p>It names Geyser's base API, a {@code compileOnly} dependency like Floodgate's, and is constructed only behind
 * the {@code isPluginEnabled("Geyser-Spigot")} guard, so a server without Geyser loads none of its classes.
 *
 * <p>A lookup that fails answers "Java player", the same degrade the Floodgate detector makes: a menu open must
 * never fail because Bedrock detection hiccuped, and a chest menu is a working answer for a Bedrock player while
 * a thrown exception is not.
 */
final class GeyserBedrockDetector implements BedrockDetector {

    @Override
    public boolean isBedrock(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            return Geyser.api().isBedrockPlayer(player);
        } catch (RuntimeException notReady) {
            return false;
        }
    }

    /** The Xbox id Geyser's connection for {@code player} carries; empty for a Java player. */
    @Override
    public Optional<String> xuid(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Connection connection = Geyser.api().connectionByUuid(player);
            String xuid = connection == null ? null : connection.xuid();
            return xuid == null || xuid.isBlank() ? Optional.empty() : Optional.of(xuid);
        } catch (RuntimeException notReady) {
            return Optional.empty();
        }
    }

    /** The client from Geyser's own connection: its platform, input mode, screen layout, version and language. */
    @Override
    public Optional<BedrockClient> client(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Connection connection = Geyser.api().connectionByUuid(player);
            if (connection == null) {
                return Optional.empty();
            }
            return Optional.of(new BedrockClient(
                    device(name(connection.platform())),
                    input(name(connection.inputMode())),
                    "POCKET".equals(name(connection.uiProfile()))
                            ? BedrockClient.Layout.POCKET
                            : BedrockClient.Layout.CLASSIC,
                    orEmpty(connection.version()),
                    orEmpty(connection.languageCode())));
        } catch (RuntimeException notReady) {
            return Optional.empty();
        }
    }

    /**
     * The constant's name. Mapped by name rather than by constant, so a constant a newer Geyser adds lands in the
     * fallback instead of failing to link.
     */
    private static String name(@Nullable Enum<?> constant) {
        return constant == null ? "" : constant.name();
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    static BedrockClient.Device device(String platform) {
        return switch (platform) {
            case "GOOGLE" -> BedrockClient.Device.ANDROID;
            case "IOS" -> BedrockClient.Device.IOS;
            case "OSX" -> BedrockClient.Device.MACOS;
            case "AMAZON" -> BedrockClient.Device.FIRE_OS;
            case "UWP", "WIN32" -> BedrockClient.Device.WINDOWS;
            case "PS4" -> BedrockClient.Device.PLAYSTATION;
            case "NX" -> BedrockClient.Device.SWITCH;
            case "XBOX" -> BedrockClient.Device.XBOX;
            case "GEARVR", "HOLOLENS" -> BedrockClient.Device.VR;
            case "UNKNOWN", "" -> BedrockClient.Device.UNKNOWN;
            default -> BedrockClient.Device.OTHER;
        };
    }

    static BedrockClient.Input input(String mode) {
        return switch (mode) {
            case "KEYBOARD_MOUSE" -> BedrockClient.Input.KEYBOARD_MOUSE;
            case "TOUCH" -> BedrockClient.Input.TOUCH;
            case "CONTROLLER" -> BedrockClient.Input.CONTROLLER;
            case "VR" -> BedrockClient.Input.VR;
            default -> BedrockClient.Input.UNKNOWN;
        };
    }

    @Override
    public String backend() {
        return "geyser";
    }
}
