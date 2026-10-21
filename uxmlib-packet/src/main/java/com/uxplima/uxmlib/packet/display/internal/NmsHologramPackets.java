package com.uxplima.uxmlib.packet.display.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.packet.Bundles;
import com.uxplima.uxmlib.packet.Components;
import com.uxplima.uxmlib.packet.EntityIds;
import com.uxplima.uxmlib.packet.Reflect;
import com.uxplima.uxmlib.packet.VanillaEntityTypes;
import com.uxplima.uxmlib.packet.display.HologramAppearance;
import com.uxplima.uxmlib.packet.display.HologramPackets;
import com.uxplima.uxmlib.pipeline.PacketSender;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The sole NMS-bearing class of the packet hologram: it builds the real Mojang-mapped add-entity, set-entity-data
 * and remove-entities packets that put a text display in a client and never on the server. Quarantining
 * {@code net.minecraft} to one class follows the precedent of {@code NmsNametagPackets} and
 * {@code NmsDisplayTextPackets}, so everything above the port stays pure and unit-testable against a fake.
 *
 * <p>Built against the Mojang-mapped dev bundle; Paper's runtime remapper maps these back to the server's own
 * mappings at load. The {@code Display} and {@code Display.TextDisplay} data-watcher accessors are
 * package-private static fields, so they are read once at construction through the shared {@link Reflect}
 * helper: the accessor object carries its own network id, which keeps us off the volatile integer indices.
 */
public final class NmsHologramPackets implements HologramPackets {

    private final PacketSender sender;

    private final EntityDataAccessor<net.minecraft.network.chat.Component> textAccessor;
    private final EntityDataAccessor<Byte> billboardAccessor;
    private final EntityDataAccessor<Integer> backgroundAccessor;
    private final EntityDataAccessor<Byte> textOpacityAccessor;
    private final EntityDataAccessor<Byte> styleFlagsAccessor;
    private final EntityDataAccessor<Integer> lineWidthAccessor;
    private final EntityDataAccessor<Float> viewRangeAccessor;
    private final EntityDataAccessor<org.joml.Vector3fc> translationAccessor;
    private final EntityDataAccessor<org.joml.Vector3fc> scaleAccessor;
    private final EntityDataAccessor<Integer> glowAccessor;
    private final EntityDataAccessor<Integer> brightnessAccessor;
    private final EntityDataAccessor<Float> shadowRadiusAccessor;
    private final EntityDataAccessor<Float> shadowStrengthAccessor;
    private final EntityDataAccessor<Byte> sharedFlagsAccessor;
    private final EntityDataAccessor<net.minecraft.world.item.ItemStack> itemAccessor;
    private final EntityDataAccessor<net.minecraft.world.level.block.state.BlockState> blockAccessor;
    private final EntityDataAccessor<Float> interactionWidthAccessor;
    private final EntityDataAccessor<Float> interactionHeightAccessor;
    private final EntityDataAccessor<Boolean> interactionResponseAccessor;
    /** The shared-flag bit that makes an entity glow, read off the class rather than written as 0x40. */
    private final int glowingFlag;

    public NmsHologramPackets(PacketSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
        // Off the registry, not off a constant: the class the entity-type constants live on has been renamed
        // across Minecraft lines, while the registry key has not.
        // Read each accessor once here and hold it in a final field, keeping the reflection off every send.
        this.textAccessor = Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_ID");
        this.billboardAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
        this.backgroundAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
        this.textOpacityAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_OPACITY_ID");
        this.styleFlagsAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID");
        this.lineWidthAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_LINE_WIDTH_ID");
        this.viewRangeAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_VIEW_RANGE_ID");
        this.translationAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_TRANSLATION_ID");
        this.scaleAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_SCALE_ID");
        this.glowAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_GLOW_COLOR_OVERRIDE_ID");
        this.brightnessAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_BRIGHTNESS_OVERRIDE_ID");
        this.shadowRadiusAccessor = Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_SHADOW_RADIUS_ID");
        this.shadowStrengthAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.class, "DATA_SHADOW_STRENGTH_ID");
        this.sharedFlagsAccessor = Reflect.accessor(net.minecraft.world.entity.Entity.class, "DATA_SHARED_FLAGS_ID");
        this.glowingFlag = Reflect.intConstant(net.minecraft.world.entity.Entity.class, "FLAG_GLOWING");
        this.itemAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.ItemDisplay.class, "DATA_ITEM_STACK_ID");
        this.blockAccessor =
                Reflect.accessor(net.minecraft.world.entity.Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");
        this.interactionWidthAccessor = Reflect.accessor(net.minecraft.world.entity.Interaction.class, "DATA_WIDTH_ID");
        this.interactionHeightAccessor =
                Reflect.accessor(net.minecraft.world.entity.Interaction.class, "DATA_HEIGHT_ID");
        this.interactionResponseAccessor =
                Reflect.accessor(net.minecraft.world.entity.Interaction.class, "DATA_RESPONSE_ID");
    }

    @Override
    public int allocateEntityId() {
        return EntityIds.next();
    }

    @Override
    public Object spawnPacket(
            int entityId, String entityTypeKey, double x, double y, double z, float yaw, float pitch) {
        Objects.requireNonNull(entityTypeKey, "entityTypeKey");
        // Off the registry, so a type is named by its key and an unknown key fails here rather than on a client.
        EntityType<?> type = VanillaEntityTypes.of(entityTypeKey);
        return new ClientboundAddEntityPacket(
                entityId, new UUID(0L, entityId), x, y, z, pitch, yaw, type, 0, Vec3.ZERO, yaw);
    }

    @Override
    public Object metadataPacket(int entityId, Component text, HologramAppearance appearance, Vector3f translation) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(translation, "translation");
        return new ClientboundSetEntityDataPacket(entityId, dataValues(text, appearance, translation));
    }

    @Override
    public Object itemMetadata(int entityId, ItemStack item, HologramAppearance appearance, Vector3f translation) {
        Objects.requireNonNull(item, "item");
        List<SynchedEntityData.DataValue<?>> values = displayValues(appearance, translation);
        values.add(SynchedEntityData.DataValue.create(itemAccessor, CraftItemStack.asNMSCopy(item)));
        return new ClientboundSetEntityDataPacket(entityId, values);
    }

    @Override
    public Object blockMetadata(int entityId, BlockData block, HologramAppearance appearance, Vector3f translation) {
        Objects.requireNonNull(block, "block");
        List<SynchedEntityData.DataValue<?>> values = displayValues(appearance, translation);
        values.add(SynchedEntityData.DataValue.create(blockAccessor, ((CraftBlockData) block).getState()));
        return new ClientboundSetEntityDataPacket(entityId, values);
    }

    @Override
    public Object interactionSize(int entityId, float width, float height) {
        return new ClientboundSetEntityDataPacket(
                entityId,
                List.of(
                        SynchedEntityData.DataValue.create(interactionWidthAccessor, width),
                        SynchedEntityData.DataValue.create(interactionHeightAccessor, height),
                        SynchedEntityData.DataValue.create(interactionResponseAccessor, true)));
    }

    /** What every display has, text or not: the turn, the reach, the place and size, and the optional looks. */
    private List<SynchedEntityData.DataValue<?>> displayValues(HologramAppearance appearance, Vector3f translation) {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(translation, "translation");
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(10);
        values.add(SynchedEntityData.DataValue.create(billboardAccessor, billboardId(appearance.billboard())));
        values.add(SynchedEntityData.DataValue.create(viewRangeAccessor, appearance.viewRange()));
        values.add(SynchedEntityData.DataValue.create(translationAccessor, new Vector3f(translation)));
        values.add(SynchedEntityData.DataValue.create(scaleAccessor, appearance.scale()));
        addOptional(values, appearance);
        return values;
    }

    private List<SynchedEntityData.DataValue<?>> dataValues(
            Component text, HologramAppearance appearance, Vector3f translation) {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(14);
        values.add(SynchedEntityData.DataValue.create(textAccessor, Components.asVanilla(text)));
        values.add(SynchedEntityData.DataValue.create(billboardAccessor, billboardId(appearance.billboard())));
        values.add(SynchedEntityData.DataValue.create(backgroundAccessor, appearance.backgroundArgb()));
        values.add(SynchedEntityData.DataValue.create(textOpacityAccessor, (byte) appearance.textOpacity()));
        values.add(SynchedEntityData.DataValue.create(styleFlagsAccessor, styleFlags(appearance)));
        values.add(SynchedEntityData.DataValue.create(lineWidthAccessor, appearance.lineWidth()));
        values.add(SynchedEntityData.DataValue.create(viewRangeAccessor, appearance.viewRange()));
        values.add(SynchedEntityData.DataValue.create(translationAccessor, new Vector3f(translation)));
        values.add(SynchedEntityData.DataValue.create(scaleAccessor, appearance.scale()));
        addOptional(values, appearance);
        return values;
    }

    /** The data-watcher byte for the billboard mode, matching vanilla {@code Display.BillboardConstraints}. */
    private static byte billboardId(Display.Billboard billboard) {
        return switch (billboard) {
            case FIXED -> (byte) 0;
            case VERTICAL -> (byte) 1;
            case HORIZONTAL -> (byte) 2;
            case CENTER -> (byte) 3;
        };
    }

    /**
     * The five properties that mean nothing until an operator asks for them.
     *
     * <p>Each is written only when it was set. Writing the sentinel instead would tell the client that the
     * text is unlit, unshadowed and outlined in black, which is not what "left alone" looks like.
     *
     * <p>The outline needs two fields, not one: the colour override says which colour, and the entity's own
     * glowing flag says to draw an outline at all. A colour on its own draws nothing, and that is the bug
     * every hand-rolled version of this has.
     */
    private void addOptional(List<SynchedEntityData.DataValue<?>> values, HologramAppearance appearance) {
        if (appearance.hasGlow()) {
            values.add(SynchedEntityData.DataValue.create(glowAccessor, appearance.glowArgb()));
            values.add(SynchedEntityData.DataValue.create(sharedFlagsAccessor, (byte) glowingFlag));
        }
        if (appearance.hasBrightness()) {
            values.add(SynchedEntityData.DataValue.create(brightnessAccessor, appearance.brightnessPacked()));
        }
        if (appearance.hasShadow()) {
            if (appearance.shadowRadius() != HologramAppearance.VANILLA_SHADOW) {
                values.add(SynchedEntityData.DataValue.create(shadowRadiusAccessor, appearance.shadowRadius()));
            }
            if (appearance.shadowStrength() != HologramAppearance.VANILLA_SHADOW) {
                values.add(SynchedEntityData.DataValue.create(shadowStrengthAccessor, appearance.shadowStrength()));
            }
        }
    }

    /** OR together the {@code TextDisplay} style bits the appearance selects. */
    private static byte styleFlags(HologramAppearance appearance) {
        byte flags = 0;
        if (appearance.textShadow()) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_SHADOW;
        }
        if (appearance.seeThrough()) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_SEE_THROUGH;
        }
        if (appearance.alignment() == TextDisplay.TextAlignment.LEFT) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_ALIGN_LEFT;
        } else if (appearance.alignment() == TextDisplay.TextAlignment.RIGHT) {
            flags |= net.minecraft.world.entity.Display.TextDisplay.FLAG_ALIGN_RIGHT;
        }
        return flags;
    }

    @Override
    public Object removePacket(int[] entityIds) {
        Objects.requireNonNull(entityIds, "entityIds");
        return new ClientboundRemoveEntitiesPacket(entityIds.clone());
    }

    @Override
    public Object bundle(List<Object> packets) {
        return Bundles.of(packets);
    }

    @Override
    public void send(Player viewer, Object packet) {
        sender.send(viewer, packet);
    }
}
