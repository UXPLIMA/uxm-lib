package com.uxplima.uxmlib.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.floodgate.util.InputMode;
import org.geysermc.floodgate.util.UiProfile;
import org.jspecify.annotations.Nullable;

/**
 * The Floodgate-backed {@link BedrockDetector}, delegating to the Floodgate SDK. This is the one class that names
 * {@code org.geysermc.floodgate}; it is instantiated only by {@code BedrockDetector.forServer} behind the
 * {@code isPluginEnabled("floodgate")} guard, so it never loads on a Java-only server and a
 * {@code NoClassDefFoundError} is impossible there.
 *
 * <p>The SDK call is wrapped defensively: a Floodgate that is present but mid-reload or otherwise not ready can
 * return a null instance or throw, and a menu open must never fail because Bedrock detection hiccuped, so any
 * runtime failure degrades to "treat them as a Java player" ({@code false}).
 */
final class FloodgateBedrockDetector implements BedrockDetector {

    @Override
    public boolean isBedrock(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player);
        } catch (RuntimeException notReady) {
            return false;
        }
    }

    @Override
    public Optional<String> xuid(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            FloodgatePlayer bedrock = FloodgateApi.getInstance().getPlayer(player);
            String xuid = bedrock == null ? null : bedrock.getXuid();
            return xuid == null || xuid.isBlank() ? Optional.empty() : Optional.of(xuid);
        } catch (RuntimeException notReady) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<BedrockClient> client(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            FloodgatePlayer bedrock = FloodgateApi.getInstance().getPlayer(player);
            if (bedrock == null) {
                return Optional.empty();
            }
            return Optional.of(new BedrockClient(
                    device(bedrock.getDeviceOs()),
                    input(bedrock.getInputMode()),
                    bedrock.getUiProfile() == UiProfile.POCKET
                            ? BedrockClient.Layout.POCKET
                            : BedrockClient.Layout.CLASSIC,
                    orEmpty(bedrock.getVersion()),
                    orEmpty(bedrock.getLanguageCode())));
        } catch (RuntimeException notReady) {
            return Optional.empty();
        }
    }

    @Override
    public String namePrefix() {
        try {
            return orEmpty(FloodgateApi.getInstance().getPlayerPrefix());
        } catch (RuntimeException notReady) {
            return "";
        }
    }

    @Override
    public String backend() {
        return "floodgate";
    }

    private static BedrockClient.Device device(@Nullable DeviceOs os) {
        if (os == null) {
            return BedrockClient.Device.UNKNOWN;
        }
        return switch (os) {
            case GOOGLE -> BedrockClient.Device.ANDROID;
            case IOS, TVOS -> BedrockClient.Device.IOS;
            case OSX -> BedrockClient.Device.MACOS;
            case AMAZON -> BedrockClient.Device.FIRE_OS;
            case UWP, WIN32 -> BedrockClient.Device.WINDOWS;
            case PS4 -> BedrockClient.Device.PLAYSTATION;
            case NX -> BedrockClient.Device.SWITCH;
            case XBOX -> BedrockClient.Device.XBOX;
            case GEARVR, HOLOLENS -> BedrockClient.Device.VR;
            case UNKNOWN -> BedrockClient.Device.UNKNOWN;
            default -> BedrockClient.Device.OTHER;
        };
    }

    private static BedrockClient.Input input(@Nullable InputMode mode) {
        if (mode == null) {
            return BedrockClient.Input.UNKNOWN;
        }
        return switch (mode) {
            case KEYBOARD_MOUSE -> BedrockClient.Input.KEYBOARD_MOUSE;
            case TOUCH -> BedrockClient.Input.TOUCH;
            case CONTROLLER -> BedrockClient.Input.CONTROLLER;
            case VR -> BedrockClient.Input.VR;
            default -> BedrockClient.Input.UNKNOWN;
        };
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
