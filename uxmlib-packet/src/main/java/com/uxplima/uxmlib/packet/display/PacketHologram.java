package com.uxplima.uxmlib.packet.display;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A hologram that is sent and never spawned. The viewer sees floating text at a fixed point in the world; the
 * server holds no entity for it at all.
 *
 * <h2>Why this exists beside {@code Holograms.spawn}</h2>
 *
 * <p>{@code Holograms.spawn} creates a real {@code TextDisplay}. That is the right answer when an operator
 * wants to walk up to the hologram and click it, and it stays. It is the wrong answer when the hologram is
 * decoration: a real entity is in the entity count, is written to the region file, comes back after a chunk
 * unload only if the server agrees, and can be reached by anything that reaches entities. A shop that floats
 * an item over a chest learned that the hard way, because the item was a real dropped entity and the market
 * duplicated it.
 *
 * <p>This costs the server nothing. The display exists only in the clients that were sent it, so it cannot be
 * hit, moved, duplicated, swept by an entity clearer or lost to a chunk unload.
 *
 * <h2>Distance, and going away</h2>
 *
 * <p>Every refresh the hologram asks the anchor's world who is within {@code viewDistance} blocks. A player
 * who comes into range is sent the spawn frame, a player who leaves it is sent a remove, and a player who
 * stays is asked for their lines again and sent the ones that changed (so a per-viewer line, a countdown, a
 * balance or a language they just picked stays current, and a line that did not change costs nothing). When
 * nobody is in range no packet is written at all: the hologram simply is not there.
 *
 * <h2>Threading</h2>
 *
 * <p>Every frame runs through {@link Scheduler#regionTimer}, so it executes on the anchor's region thread:
 * the one thread where reading that world's players is safe. {@link #show} may be called from any thread,
 * because the first frame is scheduled onto that region rather than run inline.
 */
public final class PacketHologram {

    /** Refresh cadence when a caller names none: every half-second. */
    public static final Duration DEFAULT_REFRESH_PERIOD = Duration.ofMillis(500);

    /** How far a viewer may stand from the anchor and still be sent the hologram, when a caller names none. */
    public static final double DEFAULT_VIEW_DISTANCE = 32.0;

    private final HologramPackets packets;
    private final Location anchor;
    private final double viewDistanceSquared;

    /** Entity ids for the stack, index 0 = top line. Grown on demand; an id stays bound to its line index. */
    private final List<Integer> lineIds = new ArrayList<>();

    /** Everyone currently being shown the hologram, and how many lines each of them has. */
    private final Map<UUID, Shown> shown = new HashMap<>();

    private Function<Player, List<PacketLine>> lines;
    private HologramAppearance appearance;
    private Predicate<Player> audience = viewer -> true;
    private @Nullable TaskHandle refresh;
    private boolean removed;

    private PacketHologram(
            HologramPackets packets,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<PacketLine>> lines,
            double viewDistance) {
        this.packets = packets;
        this.anchor = anchor;
        this.appearance = appearance;
        this.lines = lines;
        this.viewDistanceSquared = viewDistance * viewDistance;
    }

    /** Show a packet hologram of text at {@code anchor} with the default view distance and refresh period. */
    public static PacketHologram show(
            HologramPackets packets,
            Scheduler scheduler,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<Component>> text) {
        return show(packets, scheduler, anchor, appearance, text, DEFAULT_VIEW_DISTANCE, DEFAULT_REFRESH_PERIOD);
    }

    /**
     * Show a packet hologram of text at {@code anchor} and start its refresh loop.
     *
     * <p>{@code text} is asked for one viewer's lines at a time, on the anchor's region thread, on every
     * frame, so it must be cheap and free of side effects. It must return at least one line.
     *
     * @param viewDistance how far a viewer may stand from the anchor and still be sent the hologram
     * @param period how often the audience and the text are recomputed
     */
    public static PacketHologram show(
            HologramPackets packets,
            Scheduler scheduler,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<Component>> text,
            double viewDistance,
            Duration period) {
        return showLines(packets, scheduler, anchor, appearance, texts(text), viewDistance, period);
    }

    /** Show a packet hologram of any kind of line at {@code anchor} with the default distance and period. */
    public static PacketHologram showLines(
            HologramPackets packets,
            Scheduler scheduler,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<PacketLine>> lines) {
        return showLines(packets, scheduler, anchor, appearance, lines, DEFAULT_VIEW_DISTANCE, DEFAULT_REFRESH_PERIOD);
    }

    /**
     * Show a packet hologram of any kind of line at {@code anchor} and start its refresh loop. Every line is
     * spawned facing the anchor's yaw and pitch, which is what a fixed billboard shows.
     *
     * <p>{@code lines} is asked for one viewer's lines at a time, on the anchor's region thread, on every
     * frame, so it must be cheap and free of side effects. It must return at least one line.
     *
     * @param viewDistance how far a viewer may stand from the anchor and still be sent the hologram
     * @param period how often the audience and the lines are recomputed
     */
    public static PacketHologram showLines(
            HologramPackets packets,
            Scheduler scheduler,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<PacketLine>> lines,
            double viewDistance,
            Duration period) {
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(anchor.getWorld(), "anchor world");
        if (viewDistance <= 0) {
            throw new IllegalArgumentException("viewDistance must be > 0, was " + viewDistance);
        }
        PacketHologram hologram = new PacketHologram(packets, anchor.clone(), appearance, lines, viewDistance);
        // The first frame is scheduled rather than run inline, so show() is safe to call from any thread.
        scheduler.region(anchor, hologram::update);
        hologram.refresh = scheduler.regionTimer(anchor, period, period, handle -> hologram.update());
        return hologram;
    }

    /**
     * Recompute the audience and the text once, and reconcile every client. The refresh loop calls this; a
     * caller may call it too, on the anchor's region thread, to push a change out without waiting a period.
     */
    public void update() {
        if (removed) {
            return;
        }
        Map<UUID, Player> near = nearby();
        for (Map.Entry<UUID, Player> viewer : near.entrySet()) {
            reconcile(viewer.getKey(), viewer.getValue());
        }
        removeDeparted(near.keySet());
    }

    /** Replace the per-viewer text. It takes effect on the next {@link #update()}. */
    public void setText(Function<Player, List<Component>> next) {
        this.lines = texts(Objects.requireNonNull(next, "next"));
    }

    /** Replace the per-viewer lines, of any kind. It takes effect on the next {@link #update()}. */
    public void setLines(Function<Player, List<PacketLine>> next) {
        this.lines = Objects.requireNonNull(next, "next");
    }

    /**
     * Narrow who may see the hologram, on top of the view distance: a permission, a list of chosen players. A
     * player who leaves the audience is sent a remove on the next frame and one who joins it the whole stack.
     * Asked on the anchor's region thread for every player in range, every frame, so it must be cheap.
     */
    public void setAudience(Predicate<Player> mayView) {
        this.audience = Objects.requireNonNull(mayView, "mayView");
    }

    /** Replace the look. It takes effect on the next {@link #update()}. */
    public void setAppearance(HologramAppearance next) {
        this.appearance = Objects.requireNonNull(next, "next");
    }

    /** How many players are being shown the hologram right now. */
    public int viewerCount() {
        return shown.size();
    }

    /** The point the hologram floats at. A copy, so a caller cannot move it by editing what it read. */
    public Location anchor() {
        return anchor.clone();
    }

    /** Take the hologram off every client that has it and stop the refresh loop. Safe to call twice. */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (Shown viewer : shown.values()) {
            despawn(viewer.player(), viewer.lines().size());
        }
        shown.clear();
        TaskHandle task = refresh;
        if (task != null) {
            task.cancel();
        }
    }

    /** The online players inside the view distance of the anchor and inside the audience, keyed by id. */
    private Map<UUID, Player> nearby() {
        World world = anchor.getWorld();
        if (world == null) {
            return Map.of();
        }
        Map<UUID, Player> near = new HashMap<>();
        for (Player player : world.getPlayers()) {
            @Nullable Location at = player.getLocation();
            if (at != null && at.distanceSquared(anchor) <= viewDistanceSquared && audience.test(player)) {
                near.put(player.getUniqueId(), player);
            }
        }
        return near;
    }

    /**
     * A viewer whose client holds a stack of the same height and the same kinds of entity is sent the lines that
     * differ from what it holds, and nothing when none does, so a frame costs a packet only where something
     * changed. A new appearance differs on every line. A newcomer, or a viewer whose stack changed height or
     * kind, is sent the whole spawn frame: the client has to be told about an entity, of the right type, before
     * it can be painted.
     */
    private void reconcile(UUID id, Player player) {
        List<PacketLine> next = linesFor(player);
        Shown current = shown.get(id);
        if (current != null && sameEntities(current.lines(), next)) {
            repaint(player, current, next);
            shown.put(id, new Shown(player, List.copyOf(next), appearance));
            return;
        }
        if (current != null) {
            despawn(player, current.lines().size());
        }
        spawn(player, next);
        shown.put(id, new Shown(player, List.copyOf(next), appearance));
    }

    /** Whether two stacks are the same entities on the client: as many lines, each of the same entity type. */
    private static boolean sameEntities(List<PacketLine> held, List<PacketLine> next) {
        return held.size() == next.size()
                && IntStream.range(0, next.size())
                        .allMatch(i ->
                                held.get(i).entityTypeKey().equals(next.get(i).entityTypeKey()));
    }

    /** Re-send the metadata of each line the viewer's client holds differently, to a stack of the same entities. */
    private void repaint(Player player, Shown held, List<PacketLine> next) {
        boolean restyled = !held.appearance().equals(appearance);
        for (int i = 0; i < next.size(); i++) {
            if (restyled || !held.lines().get(i).equals(next.get(i))) {
                @Nullable Object metadata = metadata(lineId(i), next.get(i), i, next.size());
                if (metadata != null) {
                    packets.send(player, metadata);
                }
            }
        }
    }

    /** Send one viewer the whole stack: a spawn and a metadata packet per line, as a single frame. */
    private void spawn(Player player, List<PacketLine> next) {
        List<Object> frame = new ArrayList<>(next.size() * 2);
        for (int i = 0; i < next.size(); i++) {
            int id = lineId(i);
            PacketLine line = next.get(i);
            // A display carries its offset in its own data; a mob has none, so it is spawned at its place.
            Vector3f at = line instanceof PacketLine.Mob ? translationForLine(i, next.size()) : new Vector3f();
            frame.add(packets.spawnPacket(
                    id,
                    line.entityTypeKey(),
                    anchor.getX() + at.x(),
                    anchor.getY() + at.y(),
                    anchor.getZ() + at.z(),
                    anchor.getYaw(),
                    anchor.getPitch()));
            @Nullable Object metadata = metadata(id, line, i, next.size());
            if (metadata != null) {
                frame.add(metadata);
            }
        }
        packets.send(player, packets.bundle(frame));
    }

    /** Send a remove packet to everyone who was being shown the hologram and is no longer in range. */
    private void removeDeparted(Set<UUID> stillNear) {
        for (UUID id : new HashSet<>(shown.keySet())) {
            if (!stillNear.contains(id)) {
                Shown gone = shown.remove(id);
                if (gone != null) {
                    despawn(gone.player(), gone.lines().size());
                }
            }
        }
    }

    /** Take the first {@code lines} line entities off one viewer's client. */
    private void despawn(Player player, int lines) {
        if (lines <= 0) {
            return;
        }
        int[] ids = new int[lines];
        for (int i = 0; i < lines; i++) {
            ids[i] = lineId(i);
        }
        packets.send(player, packets.removePacket(ids));
    }

    /** The metadata packet for one line and its own offset within the stack, or none for a mob. */
    private @Nullable Object metadata(int id, PacketLine line, int index, int lineCount) {
        Vector3f translation = translationForLine(index, lineCount);
        return switch (line) {
            case PacketLine.Text text -> packets.metadataPacket(id, text.text(), appearance, translation);
            case PacketLine.Item item -> packets.itemMetadata(id, item.item(), appearance, translation);
            case PacketLine.Block block -> packets.blockMetadata(id, block.block(), appearance, translation);
            case PacketLine.Mob mob -> null;
        };
    }

    /** Offset a line within the stack: line 0 is the top, so an earlier index sits higher. */
    private Vector3f translationForLine(int index, int lineCount) {
        return appearance.translation().add(0f, (lineCount - 1 - index) * appearance.lineGap(), 0f);
    }

    /** Resolve, allocating if needed, the stable entity id bound to line {@code index}. */
    private int lineId(int index) {
        while (lineIds.size() <= index) {
            lineIds.add(packets.allocateEntityId());
        }
        return lineIds.get(index);
    }

    private List<PacketLine> linesFor(Player player) {
        List<PacketLine> next = lines.apply(player);
        if (next == null || next.isEmpty()) {
            throw new IllegalStateException("the hologram text returned no lines for " + player.getName());
        }
        return next;
    }

    /** Text lines as lines of the text kind, for the callers that only ever show text. */
    private static Function<Player, List<PacketLine>> texts(Function<Player, List<Component>> text) {
        return viewer -> {
            List<Component> components = text.apply(viewer);
            return components == null
                    ? List.of()
                    : components.stream().map(PacketLine::text).toList();
        };
    }

    /** One viewer being shown the hologram: who they are, and the lines and look their client is holding. */
    private record Shown(Player player, List<PacketLine> lines, HologramAppearance appearance) {}
}
