package com.uxplima.uxmlib.hud.nametag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A sink that records what it was asked to show, so the registry's composition is asserted without a server. */
final class FakeNametagSink implements NametagSink {

    private final Map<UUID, ComposedNametag> shown = new HashMap<>();
    private final List<UUID> cleared = new ArrayList<>();
    private int clearAllCalls;

    @Override
    public void apply(UUID player, String entry, ComposedNametag name) {
        shown.put(player, name);
    }

    @Override
    public void clear(UUID player, String entry) {
        shown.remove(player);
        cleared.add(player);
    }

    @Override
    public void clearAll() {
        shown.clear();
        clearAllCalls++;
    }

    @Nullable ComposedNametag shown(UUID player) {
        return shown.get(player);
    }

    List<UUID> cleared() {
        return cleared;
    }

    int clearAllCalls() {
        return clearAllCalls;
    }
}
