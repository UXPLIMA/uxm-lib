package com.uxplima.uxmlib.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;

import org.jspecify.annotations.Nullable;

/**
 * A detector that asks {@link BedrockDetector#forServer} again until a backend answers, then keeps that one.
 *
 * <p>A plugin that enables at {@code STARTUP} runs before every plugin that enables after the worlds, and Floodgate
 * is one of those, whatever load order the first plugin declares. Choosing once at that moment chose
 * {@link BedrockDetector#NONE} for the life of the server. Asking again costs two plugin lookups per question
 * until a backend is found, and nothing after it.
 */
final class ResolvingBedrockDetector implements BedrockDetector {

    private final Server server;
    private volatile @Nullable BedrockDetector found;

    ResolvingBedrockDetector(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    private BedrockDetector current() {
        BedrockDetector known = found;
        if (known != null) {
            return known;
        }
        BedrockDetector now = BedrockDetector.forServer(server);
        if (now != NONE) {
            found = now;
        }
        return now;
    }

    @Override
    public boolean isBedrock(UUID player) {
        return current().isBedrock(player);
    }

    @Override
    public Optional<String> xuid(UUID player) {
        return current().xuid(player);
    }

    @Override
    public String backend() {
        return current().backend();
    }
}
