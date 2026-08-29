package com.uxplima.uxmlib.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

/**
 * Builds and spawns native-Display holograms. Configure lines and appearance with {@link #builder()},
 * then either inspect the {@link HologramSpec} (pure, for tests) or {@link Builder#spawnAt(Location)}
 * the live entity. Spawning uses {@code World.spawn(loc, TextDisplay.class, initializer)} so the text is
 * set before the entity is added to the world (no one-tick flash). Call {@code spawnAt} on the target
 * region's thread; on Folia, schedule it.
 */
public final class Holograms {

    private Holograms() {}

    /** Start configuring a text hologram. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start configuring a managed {@link ItemHologram} showing {@code item}. The builder carries the
     * {@link Display}-shared appearance (billboard, glow, view range, brightness, scale, rotation, transform):
     * the text-only properties do not apply to an item display, and {@link ModelBuilder#spawnAt(Location)}s the
     * live, viewer-controllable hologram.
     */
    public static ItemBuilder item(ItemStack item) {
        return new ItemBuilder(Objects.requireNonNull(item, "item"));
    }

    /**
     * Start configuring a managed {@link BlockHologram} showing {@code block}. The builder carries the
     * {@link Display}-shared appearance and {@link ModelBuilder#spawnAt(Location)}s the live hologram.
     */
    public static BlockBuilder block(BlockData block) {
        return new BlockBuilder(Objects.requireNonNull(block, "block"));
    }

    /**
     * Spawn a floating item display at {@code location}. Must run on that location's region thread; the
     * returned {@link ItemDisplay} is the live entity (remove it with {@link org.bukkit.entity.Entity#remove()}).
     */
    public static ItemDisplay spawnItem(Location location, ItemStack item) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(location.getWorld(), "location world");
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            Markers.stamp(entity);
        });
    }

    /** Spawn a floating player head showing the skin of the account {@code uuid}. */
    public static ItemDisplay spawnPlayerHead(Location location, java.util.UUID uuid) {
        return spawnItem(location, PlayerHeads.ofUuid(uuid));
    }

    /** Spawn a floating player head showing the skin described by a base64 skin-texture value. */
    public static ItemDisplay spawnPlayerHeadTexture(Location location, String texture) {
        return spawnItem(location, PlayerHeads.fromTexture(texture));
    }

    /** Spawn a floating player head showing the skin at a {@code textures.minecraft.net} URL. */
    public static ItemDisplay spawnPlayerHeadSkinUrl(Location location, String skinUrl) {
        return spawnItem(location, PlayerHeads.fromSkinUrl(skinUrl));
    }

    /** Spawn a floating block display at {@code location}. Must run on that location's region thread. */
    public static BlockDisplay spawnBlock(Location location, BlockData block) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(location.getWorld(), "location world");
        return location.getWorld().spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(block);
            Markers.stamp(entity);
        });
    }

    /** Fluent builder for a hologram's content and appearance. */
    public static final class Builder {
        private final List<Component> lines = new ArrayList<>();
        private Appearance appearance = Appearance.DEFAULT;

        private Builder() {}

        /** Append a text line. */
        public Builder line(Component line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /** How the display faces the viewer; {@link Display.Billboard#CENTER} (always faces) by default. */
        public Builder billboard(Display.Billboard billboard) {
            appearance = appearance.withBillboard(Objects.requireNonNull(billboard, "billboard"));
            return this;
        }

        /** Whether the text shows through blocks. */
        public Builder seeThrough(boolean seeThrough) {
            appearance = appearance.withSeeThrough(seeThrough);
            return this;
        }

        /** Give the text a glowing outline in {@code color}. */
        public Builder glow(Color color) {
            appearance = appearance.withGlow(Objects.requireNonNull(color, "color"));
            return this;
        }

        /** Set the text-panel background colour (supports ARGB). */
        public Builder background(Color color) {
            appearance = appearance.withBackground(Objects.requireNonNull(color, "color"));
            return this;
        }

        /** Set the text opacity (0 to 255 as a signed byte; {@code -1} is fully opaque). */
        public Builder textOpacity(byte opacity) {
            appearance = appearance.withTextOpacity(opacity);
            return this;
        }

        /** Set the maximum line width in pixels before wrapping. */
        public Builder lineWidth(int width) {
            appearance = appearance.withLineWidth(width);
            return this;
        }

        /** Whether the text casts a shadow. */
        public Builder textShadow(boolean shadow) {
            appearance = appearance.withTextShadow(shadow);
            return this;
        }

        /** Set how far away the hologram stays visible (a view-range multiplier). */
        public Builder viewRange(float range) {
            appearance = appearance.withViewRange(range);
            return this;
        }

        /** Override the block/sky light levels the text is rendered at. */
        public Builder brightness(Display.Brightness brightness) {
            appearance = appearance.withBrightness(Objects.requireNonNull(brightness, "brightness"));
            return this;
        }

        /** Scale the hologram uniformly (1.0 = default size). */
        public Builder scale(float factor) {
            appearance = appearance.withTransform(transformOrNone().withScale(factor));
            return this;
        }

        /** Scale the hologram per axis (1.0 = default size on that axis). */
        public Builder scale(float x, float y, float z) {
            appearance = appearance.withTransform(transformOrNone().withScale(x, y, z));
            return this;
        }

        /** Offset the hologram by {@code (x, y, z)} in display space, relative to its anchor. */
        public Builder translation(float x, float y, float z) {
            appearance = appearance.withTransform(transformOrNone().withTranslation(x, y, z));
            return this;
        }

        /** Align the text to the {@code LEFT}, {@code CENTER} or {@code RIGHT} of the line. */
        public Builder alignment(TextDisplay.TextAlignment alignment) {
            appearance = appearance.withAlignment(Objects.requireNonNull(alignment, "alignment"));
            return this;
        }

        /** Set the radius of the drop shadow cast on the ground beneath the hologram (0 = none). */
        public Builder shadowRadius(float radius) {
            appearance = appearance.withShadowRadius(radius);
            return this;
        }

        /** Set the strength (opacity falloff) of the drop shadow beneath the hologram. */
        public Builder shadowStrength(float strength) {
            appearance = appearance.withShadowStrength(strength);
            return this;
        }

        /** Rotate the hologram {@code degrees} about the vertical axis. */
        public Builder rotation(float degrees) {
            appearance = appearance.withTransform(transformOrNone().withYaw(degrees));
            return this;
        }

        /** Rotate the hologram {@code yaw} about the vertical axis and {@code pitch} about the horizontal axis. */
        public Builder rotation(float yaw, float pitch) {
            appearance = appearance.withTransform(transformOrNone().withYaw(yaw).withPitch(pitch));
            return this;
        }

        /** Set the full scale-and-rotation transform at once. */
        public Builder transform(Transform transform) {
            appearance = appearance.withTransform(Objects.requireNonNull(transform, "transform"));
            return this;
        }

        private Transform transformOrNone() {
            Transform current = appearance.transform();
            return current == null ? Transform.NONE : current;
        }

        /** The immutable specification, for inspection or reuse. Requires at least one line. */
        public HologramSpec spec() {
            return new HologramSpec(lines, appearance);
        }

        /** Spawn the hologram at {@code location}. Must run on that location's region thread. */
        public Hologram spawnAt(Location location) {
            Objects.requireNonNull(location, "location");
            return Holograms.spawn(spec(), location);
        }
    }

    /** Spawn {@code spec} at {@code location} as a tracked, manager-free {@link Hologram}. */
    static Hologram spawn(HologramSpec spec, Location location) {
        Objects.requireNonNull(location.getWorld(), "location world");
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(spec.asText());
            spec.appearance().applyTo(entity);
            Markers.stamp(entity);
        });
        return new DisplayHologram(display);
    }

    /**
     * Spawn a mixed-line hologram (text, item, and block lines together) at {@code location}, one native
     * {@code Display} per line, auto-stacked top-down from the anchor by each line's gap. Must run on that
     * location's region thread; despawn the whole column with {@link MixedHologram#remove()}.
     */
    public static MixedHologram spawnMixed(MixedHologramSpec spec, Location location) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(location, "location");
        org.bukkit.World world = Objects.requireNonNull(location.getWorld(), "location world");
        List<HologramLine> lines = spec.lines();
        List<Double> offsets = spec.stackOffsets();
        List<Display> parts = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Location at = location.clone().add(0, offsets.get(i), 0);
            parts.add(spawnLine(world, lines.get(i), at, spec.appearance()));
        }
        return new MixedHologram(parts);
    }

    private static Display spawnLine(org.bukkit.World world, HologramLine line, Location at, Appearance appearance) {
        return switch (line) {
            case HologramLine.TextLine text -> spawnTextLine(world, text, at, appearance);
            case HologramLine.ItemLine item -> spawnItem(at, item.item());
            case HologramLine.BlockLine block -> spawnBlock(at, block.block());
        };
    }

    private static Display spawnTextLine(
            org.bukkit.World world, HologramLine.TextLine line, Location at, Appearance appearance) {
        return world.spawn(at, TextDisplay.class, entity -> {
            entity.text(line.text());
            appearance.applyTo(entity);
            Markers.stamp(entity);
        });
    }

    /**
     * Create a {@link PerViewerHologram} anchored at {@code location} with {@code appearance}. Each viewer's
     * private {@code TextDisplay} is spawned viewer-restricted (invisible by default) and stamped, so it is
     * shown only to its owner and is swept like any other hologram. Set the renderer with
     * {@link PerViewerHologram#setText} and drive updates through your scheduler.
     */
    public static PerViewerHologram perViewer(Location location, Appearance appearance) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(appearance, "appearance");
        org.bukkit.World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new PerViewerHologram(
                at -> world.spawn(at, TextDisplay.class, entity -> {
                    entity.setVisibleByDefault(false);
                    appearance.applyTo(entity);
                    Markers.stamp(entity);
                }),
                location);
    }

    /**
     * The shared fluent base for the item and block builders: the {@link Display}-wide appearance setters that
     * apply to every display type (billboard, glow, view range, brightness, scale, rotation, transform). The
     * text-only setters (background, line width, shadow, see-through, opacity) are intentionally absent: they
     * have no meaning for an item or block display. Each setter returns {@code this} typed as the concrete
     * builder so a chain stays fluent.
     *
     * @param <B> the concrete builder type, returned by every setter for fluent chaining
     */
    public abstract static sealed class ModelBuilder<B extends ModelBuilder<B>> permits ItemBuilder, BlockBuilder {
        Appearance appearance = Appearance.DEFAULT;

        ModelBuilder() {}

        abstract B self();

        /** How the display faces the viewer; {@link Display.Billboard#CENTER} (always faces) by default. */
        public B billboard(Display.Billboard billboard) {
            appearance = appearance.withBillboard(Objects.requireNonNull(billboard, "billboard"));
            return self();
        }

        /** Give the model a glowing outline in {@code color}. */
        public B glow(Color color) {
            appearance = appearance.withGlow(Objects.requireNonNull(color, "color"));
            return self();
        }

        /** Set how far away the hologram stays visible (a view-range multiplier). */
        public B viewRange(float range) {
            appearance = appearance.withViewRange(range);
            return self();
        }

        /** Override the block/sky light levels the model is rendered at. */
        public B brightness(Display.Brightness brightness) {
            appearance = appearance.withBrightness(Objects.requireNonNull(brightness, "brightness"));
            return self();
        }

        /** Scale the hologram uniformly (1.0 = default size). */
        public B scale(float factor) {
            appearance = appearance.withTransform(transformOrNone().withScale(factor));
            return self();
        }

        /** Scale the hologram per axis (1.0 = default size on that axis). */
        public B scale(float x, float y, float z) {
            appearance = appearance.withTransform(transformOrNone().withScale(x, y, z));
            return self();
        }

        /** Offset the hologram by {@code (x, y, z)} in display space, relative to its anchor. */
        public B translation(float x, float y, float z) {
            appearance = appearance.withTransform(transformOrNone().withTranslation(x, y, z));
            return self();
        }

        /** Set the radius of the drop shadow cast on the ground beneath the hologram (0 = none). */
        public B shadowRadius(float radius) {
            appearance = appearance.withShadowRadius(radius);
            return self();
        }

        /** Set the strength (opacity falloff) of the drop shadow beneath the hologram. */
        public B shadowStrength(float strength) {
            appearance = appearance.withShadowStrength(strength);
            return self();
        }

        /** Rotate the hologram {@code degrees} about the vertical axis. */
        public B rotation(float degrees) {
            appearance = appearance.withTransform(transformOrNone().withYaw(degrees));
            return self();
        }

        /** Rotate the hologram {@code yaw} about the vertical axis and {@code pitch} about the horizontal axis. */
        public B rotation(float yaw, float pitch) {
            appearance = appearance.withTransform(transformOrNone().withYaw(yaw).withPitch(pitch));
            return self();
        }

        /** Set the full scale-and-rotation transform at once. */
        public B transform(Transform transform) {
            appearance = appearance.withTransform(Objects.requireNonNull(transform, "transform"));
            return self();
        }

        /** The accumulated appearance, for inspection or reuse in a test. */
        public Appearance appearance() {
            return appearance;
        }

        private Transform transformOrNone() {
            Transform current = appearance.transform();
            return current == null ? Transform.NONE : current;
        }

        /** Spawn the hologram at {@code location}. Must run on that location's region thread. */
        public abstract ModelHologram spawnAt(Location location);
    }

    /** Fluent builder for an {@link ItemHologram}'s content and {@link Display}-shared appearance. */
    public static final class ItemBuilder extends ModelBuilder<ItemBuilder> {
        private final ItemStack item;

        ItemBuilder(ItemStack item) {
            this.item = item;
        }

        @Override
        ItemBuilder self() {
            return this;
        }

        @Override
        public ItemHologram spawnAt(Location location) {
            Objects.requireNonNull(location, "location");
            org.bukkit.World world = Objects.requireNonNull(location.getWorld(), "location world");
            ItemDisplay display = world.spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                appearance.applyToDisplay(entity);
                Markers.stamp(entity);
            });
            return new ItemHologram(display);
        }
    }

    /** Fluent builder for a {@link BlockHologram}'s content and {@link Display}-shared appearance. */
    public static final class BlockBuilder extends ModelBuilder<BlockBuilder> {
        private final BlockData block;

        BlockBuilder(BlockData block) {
            this.block = block;
        }

        @Override
        BlockBuilder self() {
            return this;
        }

        @Override
        public BlockHologram spawnAt(Location location) {
            Objects.requireNonNull(location, "location");
            org.bukkit.World world = Objects.requireNonNull(location.getWorld(), "location world");
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(block);
                appearance.applyToDisplay(entity);
                Markers.stamp(entity);
            });
            return new BlockHologram(display);
        }
    }
}
