package com.uxplima.uxmlib.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;

/**
 * Tells whether a player is a Bedrock (Floodgate) player, so a caller can send them a native Bedrock form
 * instead of a chest menu.
 *
 * <p>The Java-only default is {@link #NONE}: it answers {@code false} for everyone and carries no Floodgate
 * reference, so a server without Floodgate never touches the SDK. When Floodgate is installed the
 * {@link #forServer(Server)} factory returns the Floodgate-backed detector instead: that concrete class is the
 * only place the {@code org.geysermc.floodgate} SDK is named, and the factory constructs it strictly behind the
 * {@code isPluginEnabled("floodgate")} guard, so it (and the SDK it references) never loads on a Java-only
 * server.
 *
 * <p>Geyser without Floodgate is the second supported shape. Those servers still carry Bedrock players, and
 * Geyser can name them, so the factory falls through to a Geyser-backed detector when Floodgate is absent.
 */
public interface BedrockDetector {

    /** The Java-only default: everyone is a Java player, and no Floodgate class is referenced. */
    BedrockDetector NONE = new BedrockDetector() {
        @Override
        public boolean isBedrock(UUID player) {
            return false;
        }
    };

    /**
     * Whether {@code player} connected through Floodgate as a Bedrock player.
     *
     * @param player the player's unique id; never {@code null}
     * @return {@code true} only when a Floodgate-backed detector confirms it; always {@code false} for {@link #NONE}
     */
    boolean isBedrock(UUID player);

    /**
     * The Xbox id {@code player} connected under, when the backend knows it.
     *
     * <p>It is the key every Bedrock-side service is addressed by, the skin service among them, which is why it
     * lives beside the detection rather than in a second probe of the same plugin. A Java player, a backend that
     * cannot name one, or no backend at all all answer empty.
     */
    default Optional<String> xuid(UUID player) {
        return Optional.empty();
    }

    /**
     * Which plugin answers this detector's questions, for the one line the bootstrap logs on enable. Operators
     * read it to tell the two working shapes apart, so it names the source rather than the class.
     *
     * @return {@code "none"}, {@code "floodgate"} or {@code "geyser"}
     */
    default String backend() {
        return "none";
    }

    /**
     * Selects the detector for this server: the Floodgate-backed one when the {@code floodgate} plugin is enabled,
     * the Geyser-backed one when only {@code Geyser-Spigot} is, otherwise {@link #NONE}. Each backed detector is
     * named only inside its own branch, so on a server with neither plugin their classes (and the
     * {@code org.geysermc} SDKs they reference) are never loaded.
     *
     * <p>Floodgate wins when both are installed. It is the richer source, it is a compile-time dependency rather
     * than a reflective one, and it is the path with a Bedrock link account behind it.
     *
     * @param server the running server, used only to probe plugin presence; never {@code null}
     * @return a Floodgate-backed detector when Floodgate is enabled, a Geyser-backed one when only Geyser is,
     *     else {@link #NONE}
     */
    static BedrockDetector forServer(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getPluginManager().isPluginEnabled("floodgate")) {
            return new FloodgateBedrockDetector();
        }
        if (server.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            return new GeyserBedrockDetector();
        }
        return NONE;
    }

    /**
     * A detector that asks {@link #forServer} again on each use until a backend answers, then keeps it.
     *
     * <p>For a plugin that enables at {@code STARTUP}: it runs before Floodgate whatever load order it declares,
     * so {@link #forServer} called from its enable always answers {@link #NONE}. Every other plugin keeps calling
     * {@link #forServer}, which answers once and costs nothing per use.
     *
     * @param server the running server, probed for the backend on each use until one is found; never {@code null}
     * @return a detector that is {@link #NONE} until a backend enables, and that backend from then on
     */
    static BedrockDetector resolving(Server server) {
        return new ResolvingBedrockDetector(server);
    }
}
