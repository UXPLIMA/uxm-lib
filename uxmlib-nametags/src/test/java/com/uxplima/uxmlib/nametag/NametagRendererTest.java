package com.uxplima.uxmlib.nametag;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NametagRendererTest {

    private static final int TARGET_ID = 7;

    private final UUID viewerId = UUID.randomUUID();
    private final Set<Player> trackedBy = new HashSet<>();
    private final List<String> sent = new ArrayList<>();
    private final List<Consumer<TaskHandle>> refreshes = new ArrayList<>();

    private final Player viewer = proxy(Player.class, (method, args) -> switch (method) {
        case "getUniqueId" -> viewerId;
        default -> throw new UnsupportedOperationException(method);
    });
    private final Server server = proxy(Server.class, (method, args) -> switch (method) {
        case "getPlayer" -> viewerId.equals(args[0]) ? viewer : null;
        default -> throw new UnsupportedOperationException(method);
    });
    private final Player target = proxy(Player.class, (method, args) -> switch (method) {
        case "getTrackedBy" -> Set.copyOf(trackedBy);
        case "getServer" -> server;
        case "getEntityId" -> TARGET_ID;
        case "getX", "getY", "getZ" -> 0.0;
        default -> throw new UnsupportedOperationException(method);
    });
    private final Scheduler scheduler = proxy(Scheduler.class, (method, args) -> {
        if (!method.equals("entityTimer")) {
            throw new UnsupportedOperationException(method);
        }
        @SuppressWarnings("unchecked")
        Consumer<TaskHandle> task = (Consumer<TaskHandle>) args[3];
        refreshes.add(task);
        return proxy(TaskHandle.class, (name, unused) -> null);
    });
    private final NametagRenderer renderer =
            new NametagRenderer(new RecordingPackets(), scheduler, (seer, seen) -> false);

    private void show() {
        renderer.show(target, Appearance.defaults(), () -> Set.of(viewerId), reader -> List.of(Component.text("Tag")));
    }

    private void refresh() {
        refreshes.get(0).accept(proxy(TaskHandle.class, (name, unused) -> null));
    }

    @Test
    void aClientThatDoesNotTrackTheTargetIsSentNothing() {
        // Its client would drop the mount and keep the lines at the target's feet.
        show();
        refresh();

        assertThat(sent).isEmpty();
    }

    @Test
    void aViewerGetsTheStackOnceTheirClientTracksTheTarget() {
        show();
        trackedBy.add(viewer);
        refresh();

        assertThat(sent).containsExactly("spawn", "metadata", "mount " + TARGET_ID);
    }

    @Test
    void everyRefreshSeatsTheLinesOnTheTargetAgain() {
        // A client that lost the target's entity and got it back between two refreshes holds the lines unseated.
        trackedBy.add(viewer);
        show();
        sent.clear();
        refresh();

        assertThat(sent).containsExactly("metadata", "mount " + TARGET_ID);
    }

    @Test
    void aViewerWhoseClientStopsTrackingIsDroppedAndGetsTheStackOnReturn() {
        trackedBy.add(viewer);
        show();
        trackedBy.clear();
        refresh();
        trackedBy.add(viewer);
        sent.clear();
        refresh();

        assertThat(sent).containsExactly("spawn", "metadata", "mount " + TARGET_ID);
    }

    private final class RecordingPackets implements NametagPackets {
        private int nextId = 100;

        @Override
        public int allocateEntityId() {
            return nextId++;
        }

        @Override
        public Object spawnPacket(int entityId, double x, double y, double z) {
            return "spawn";
        }

        @Override
        public Object metadataPacket(
                int entityId, Component text, Appearance appearance, int opacity, Vector3f translation) {
            return "metadata";
        }

        @Override
        public Object mountPacket(int vehicleEntityId, int[] passengerIds) {
            return "mount " + vehicleEntityId;
        }

        @Override
        public Object removePacket(int[] entityIds) {
            return "remove";
        }

        @Override
        public Object bundle(List<Object> packets) {
            return packets;
        }

        @Override
        public void send(Player to, Object packet) {
            if (packet instanceof List<?> bundle) {
                bundle.forEach(each -> sent.add(String.valueOf(each)));
            } else {
                sent.add(String.valueOf(packet));
            }
        }
    }

    private interface Answer {
        @Nullable Object answer(String method, @Nullable Object[] args);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(
                NametagRendererTest.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "toString" -> type.getSimpleName();
                        default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
                    };
                }));
    }
}
