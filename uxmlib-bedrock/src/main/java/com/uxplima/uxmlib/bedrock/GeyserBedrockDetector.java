package com.uxplima.uxmlib.bedrock;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The Geyser-backed {@link BedrockDetector}, for networks that run Geyser without Floodgate. Geyser alone still
 * knows which connections came from Bedrock, so those servers can have native forms too instead of falling back
 * to a chest menu for every Bedrock player.
 *
 * <p>Unlike its Floodgate sibling this is reached entirely by reflection: Floodgate is a {@code compileOnly}
 * dependency and can be named directly, Geyser is not one, so no {@code org.geysermc.geyser} type appears in
 * this file's signature and a server without Geyser loads none of its classes.
 *
 * <p>A lookup that fails answers "Java player", the same degrade the Floodgate detector makes: a menu open must
 * never fail because Bedrock detection hiccuped, and a chest menu is a working answer for a Bedrock player while
 * a thrown exception is not.
 */
final class GeyserBedrockDetector implements BedrockDetector {

    private static final String API_CLASS = "org.geysermc.geyser.api.GeyserApi";

    private @Nullable Method api;
    private @Nullable Method isBedrockPlayer;

    @Override
    public boolean isBedrock(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Method entry = api;
            Method probe = isBedrockPlayer;
            if (entry == null || probe == null) {
                Class<?> apiType = Class.forName(API_CLASS);
                entry = apiType.getMethod("api");
                // Resolved on the interface rather than on the returned object: Geyser's implementation class is
                // an internal one, and a method handle taken from it would be unreachable from here.
                probe = apiType.getMethod("isBedrockPlayer", UUID.class);
                api = entry;
                isBedrockPlayer = probe;
            }
            Object geyser = entry.invoke(null);
            if (geyser == null) {
                return false;
            }
            return Boolean.TRUE.equals(probe.invoke(geyser, player));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notReady) {
            return false;
        }
    }

    /**
     * The client from Geyser's own connection: its platform, input mode, screen layout, version and language. Read
     * by method and enum name, so no Geyser type is named here.
     */
    @Override
    public java.util.Optional<BedrockClient> client(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Class<?> apiType = Class.forName(API_CLASS);
            Object geyser = apiType.getMethod("api").invoke(null);
            if (geyser == null) {
                return java.util.Optional.empty();
            }
            Object connection =
                    apiType.getMethod("connectionByUuid", UUID.class).invoke(geyser, player);
            if (connection == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new BedrockClient(
                    device(name(connection, "platform")),
                    input(name(connection, "inputMode")),
                    "POCKET".equals(name(connection, "uiProfile"))
                            ? BedrockClient.Layout.POCKET
                            : BedrockClient.Layout.CLASSIC,
                    text(connection, "version"),
                    text(connection, "languageCode")));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notReady) {
            return java.util.Optional.empty();
        }
    }

    /** The name of the enum {@code method} answers, or empty. */
    private static String name(Object connection, String method) throws ReflectiveOperationException {
        Object value = publicMethod(connection, method).invoke(connection);
        return value instanceof Enum<?> constant ? constant.name() : "";
    }

    private static String text(Object connection, String method) throws ReflectiveOperationException {
        Object value = publicMethod(connection, method).invoke(connection);
        return value == null ? "" : value.toString();
    }

    /** The method as the public interface declares it: Geyser's own class is internal and cannot be invoked. */
    private static Method publicMethod(Object target, String name) throws NoSuchMethodException {
        for (Class<?> type : target.getClass().getInterfaces()) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException elsewhere) {
                // Looked for on the next interface.
            }
        }
        return target.getClass().getMethod(name);
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
