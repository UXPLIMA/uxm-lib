package com.uxplima.uxmlib.packet.npc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.Nullable;

/**
 * The seam between pure NPC logic and the NMS packet construction for a fake-player NPC. Every packet crosses
 * this boundary as an opaque {@link Object}, so this interface — and everything that depends on it — carries no
 * {@code net.minecraft} reference and stays unit-testable against a fake. The single implementation that builds
 * the real Mojang-mapped packets against the dev bundle lives behind this port in {@code npc.internal}.
 *
 * <p>A fake-player NPC is shown to a client in a fixed sequence: first a player-info entry carrying the name
 * and skin so the client knows how to render the body, then the entity spawn at a position and rotation, then
 * the look packets that aim its head and body. The client links the skin to the spawned entity because the
 * spawn UUID equals the player-info entry's profile id — so the spawn must reuse the same {@code profileId}.
 * The entry is added <em>unlisted</em> ({@code listed=false}) so the body renders but the NPC never shows as a
 * tab-list row, and the entry is kept for the entity's whole lifetime — removing the player-info entry
 * de-renders the entity on modern clients, so it stays until the NPC despawns, when {@link #tabRemove(UUID)}
 * drops it. An NPC the operator does want in the tab list is added with {@code listed=true} instead; either way
 * the entry is kept. Each method returns one built packet; {@link #send(Player, Object)} writes it to a viewer's
 * connection, so the same packet can be sent to many viewers without rebuilding it.
 */
public interface NpcPackets {

    /** Allocate a fake-entity id from the shared server counter; it never collides with a real entity. */
    int allocateEntityId();

    /**
     * Build a player-info ADD packet that seats a fully-controlled profile (name, and the skin as a
     * {@code textures} property when present) so the client can render the NPC's body, marked {@code listed} so
     * the NPC also shows as a row in the tab list. Most NPCs want {@link #tabAdd(UUID, String, TabSkin, boolean)
     * the listed overload} with {@code listed=false} instead, which renders the body without the tab-list row;
     * this listed shorthand stays for an NPC the operator does want listed.
     */
    Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin);

    /**
     * Build a player-info ADD packet exactly like {@link #tabAdd(UUID, String, TabSkin)} but with the entry's
     * {@code listed} flag set explicitly. With {@code listed=false} the entry is a known client entity — the NPC
     * body and skin render — but it is not drawn as a row in the tab list, which is how a fake-player NPC is
     * normally shown. With {@code listed=true} the NPC also appears in the tab list. Either way the entry is kept
     * for the entity's lifetime (removing it would de-render the body on modern clients); {@link #tabRemove(UUID)}
     * drops it on despawn.
     */
    Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin, boolean listed);

    /** Build a player-info REMOVE packet that drops {@code profileId} from the tab list. */
    Object tabRemove(UUID profileId);

    /**
     * Build the entity-spawn packet for a fake player at {@code x,y,z} facing {@code yaw}/{@code pitch}. The
     * spawn UUID is {@code profileId} so the client links the skin from the matching player-info entry; the
     * head is aimed at {@code yaw} too, matching the body's initial facing. This is the {@code PLAYER}
     * specialisation of {@link #spawnEntity}: the player path needs the profile id as the spawn UUID so the
     * skin binds, whereas a mob carries an opaque entity UUID with no profile behind it.
     */
    Object spawnPlayer(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch);

    /**
     * Build the entity-spawn packet for a mob NPC of {@code entityTypeKey} (a namespaced or plain entity-type
     * key, e.g. {@code "minecraft:villager"} or {@code "villager"}) at {@code x,y,z} facing {@code yaw}/{@code
     * pitch}. Unlike {@link #spawnPlayer} there is no player-info entry: a mob spawns straight through the spawn
     * packet, so {@code entityUuid} is just the opaque entity UUID (no profile, no skin binding). The same
     * generic add-entity builder backs both methods; this one resolves the server entity type from the key.
     *
     * @throws IllegalArgumentException if {@code entityTypeKey} resolves to no known entity type. The caller is
     *     expected to validate the type before calling, so this is a defensive guard rather than a routine path.
     */
    Object spawnEntity(
            int entityId, UUID entityUuid, String entityTypeKey, double x, double y, double z, float yaw, float pitch);

    /** Build a head-rotation packet that aims only the NPC's head at {@code yaw}. */
    Object headLook(int entityId, float yaw);

    /** Build a rotation-only move packet that turns the NPC's body to {@code yaw}/{@code pitch}. */
    Object bodyLook(int entityId, float yaw, float pitch);

    /** Build a teleport packet that moves the NPC to {@code x,y,z} facing {@code yaw}/{@code pitch}. */
    Object teleport(int entityId, double x, double y, double z, float yaw, float pitch);

    /** Build a packet that despawns the NPC by its entity id. */
    Object remove(int entityId);

    /**
     * Build the set-equipment packet that dresses the NPC: each {@link EquipmentSlot} in {@code items} maps to the
     * {@link ItemStack} worn there. Equipment is inherently item data, so this port takes the Bukkit
     * {@link ItemStack} directly (the implementation copies it into the server's own item form). An empty map
     * produces a packet that strips every slot the NPC could wear, so a re-render that drops a slot clears it on
     * the client rather than leaving a stale item.
     */
    Object equipment(int entityId, Map<EquipmentSlot, ItemStack> items);

    /**
     * Build the metadata packet that turns the NPC's glowing outline on or off through the entity's shared-flags
     * byte (the {@code glowing} bit). White is the default outline colour; {@link #glowColor} tints it. A fresh
     * NPC carries no other shared flags (not on fire, not sneaking), so the packet sets only the one bit.
     *
     * <p>This sets <em>only</em> the glowing bit, so it overwrites the on-fire and invisible bits of the same byte;
     * an NPC that combines glow with on-fire or invisible must use {@link #sharedFlags} instead, which composes all
     * three bits into one byte. This narrower method is kept for the common glow-only path and for callers that
     * never touch the other flags.
     */
    Object glow(int entityId, boolean glowing);

    /**
     * Build the metadata packet that sets the NPC's shared-flags byte (data item 0) from the {@code onFire},
     * {@code glowing}, and {@code invisible} bits at once, so the three compose into one value rather than three
     * packets each overwriting the whole byte. The bit positions ({@code FLAG_ONFIRE}, {@code FLAG_GLOWING},
     * {@code FLAG_INVISIBLE}) are read off {@code Entity} once at construction, so an unset flag leaves its bit
     * clear. White is still the default glow outline; {@link #glowColor} tints it independently of this byte.
     */
    Object sharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible);

    /**
     * Build the metadata packet that toggles the NPC's silence through the entity's {@code DATA_SILENT} boolean —
     * a silent entity emits no ambient or hurt sounds. The accessor lives on {@code Entity}, so it applies to every
     * NPC type (player or mob), and is read once at construction off every hot path like the shared-flags accessor.
     */
    Object silent(int entityId, boolean silent);

    /**
     * Build the scoreboard-team packet carrying every team-scoped property of one NPC at once: its collision rule,
     * its glow colour, and whether its nametag renders. The client honours all three for an entity whose name is on
     * the team, and an entity can be on only one team, so they must travel on a single packet rather than on several
     * that overwrite each other. This creates (or modifies) a team named {@code teamName}, sets its collision rule to
     * {@code ALWAYS} when {@code collidable} or {@code NEVER} when not, tints it {@code color} (or leaves it the
     * default white when {@code null}), sets its nametag visibility to {@code NEVER} when {@code hideNametag} or
     * {@code ALWAYS} when not, and seats {@code memberName} as a member. The caller uses this in place of
     * {@link #glowColor} whenever the NPC overrides any of the three; {@link #glowColorRemove} still clears the team
     * on despawn either way.
     *
     * <p>Hiding the nametag through the team is the one mechanism that works for a fake player, whose nametag is its
     * player-info profile name rather than a custom-name field: the profile name stays the NPC's real name (so the
     * team membership and the tab entry stay well-formed) and the client is told not to draw it.
     *
     * @param teamName the team name to create or modify (the same per-NPC team {@link #glowColor} uses)
     * @param memberName the NPC's profile name, seated as the team's member
     * @param color the glow colour, or {@code null} for the default white outline
     * @param collidable whether the NPC collides with players (team collision rule {@code ALWAYS} vs {@code NEVER})
     * @param hideNametag whether the NPC's nametag is hidden (team nametag visibility {@code NEVER} vs {@code ALWAYS})
     */
    Object team(
            String teamName, String memberName, @Nullable NamedColor color, boolean collidable, boolean hideNametag);

    /**
     * Build the metadata packet that freezes the NPC in {@code pose} through the entity's {@code DATA_POSE} field.
     * A pose only renders where the entity type supports it (a player or a humanoid mob), so an NPC whose type
     * cannot strike the pose simply ignores it — the packet is harmless either way. The accessor is read once at
     * construction, like the glow accessor, so this stays off the reflection path on every send.
     */
    Object pose(int entityId, NpcPose pose);

    /**
     * Build the attribute packet that resizes the NPC to {@code scale} through the {@code minecraft:generic.scale}
     * attribute (1.0 is the natural size; 2.0 is twice as tall, 0.5 half). The attribute applies to every entity
     * type. The plugin command is the primary clamp; the implementation guards only against a non-finite or
     * non-positive value, which the protocol cannot represent.
     */
    Object scale(int entityId, double scale);

    /**
     * Build the metadata packet that toggles an {@code AgeableMob}'s baby/adult state through the {@code
     * AgeableMob.DATA_BABY_ID} field — the boolean ({@code true} is a baby, {@code false} an adult) carried by the
     * breeding animal line (cows, pigs, …), the villager line, and the hoglin (all real {@code AgeableMob}
     * subclasses). The caller must only ever send this to a type that actually extends {@code AgeableMob}: the
     * baby flag's data index is allocated per class hierarchy, so it differs between {@code AgeableMob} and the
     * monster-family baby mobs (the zombie line, piglins, zoglins) — those carry their own baby field at a
     * different index and have their own builders ({@link #zombieBaby}, {@link #piglinBaby}, {@link #zoglinBaby}).
     * Sending this packet to one of them, or to a non-ageable type (a creeper, a slime), would land the value on
     * an unrelated field. Picking the right builder for the type is the plugin's per-type concern, not this one's.
     */
    Object baby(int entityId, boolean baby);

    /**
     * Build the metadata packet that toggles a zombie-line mob's baby/adult state through {@code
     * Zombie.DATA_BABY_ID} — the zombie family's own baby boolean, at a different data index than {@link #baby}'s
     * {@code AgeableMob} one because zombies extend {@code Monster}, not {@code AgeableMob}. Send this only to a
     * zombie, husk, drowned, zombie villager, or zombified piglin; any other type has no field at that index.
     */
    Object zombieBaby(int entityId, boolean baby);

    /**
     * Build the metadata packet that toggles a piglin's baby/adult state through {@code Piglin.DATA_BABY_ID} — the
     * piglin's own baby boolean, again at a different index than {@link #baby}'s because piglins extend {@code
     * Monster}. Send this only to a piglin (not a piglin brute, which has no baby form); any other type has no
     * field at that index.
     */
    Object piglinBaby(int entityId, boolean baby);

    /**
     * Build the metadata packet that toggles a zoglin's baby/adult state through {@code Zoglin.DATA_BABY_ID} — the
     * zoglin's own baby boolean, at a different index than {@link #baby}'s because zoglins extend {@code Monster}.
     * Send this only to a zoglin; any other type has no field at that index.
     */
    Object zoglinBaby(int entityId, boolean baby);

    /**
     * Build the metadata packet that sets a villager's appearance — its biome {@code type} (the registry name of a
     * villager type, e.g. {@code plains}/{@code desert}), its {@code profession} (e.g. {@code farmer}/{@code
     * librarian}), and its {@code level} (1–5, the badge tier) — through the villager's {@code DATA_VILLAGER_DATA}
     * field. The type and profession are resolved by name off the server's villager-type and villager-profession
     * registries, both defaulted registries: an unknown name falls back to the registry default rather than
     * throwing, so a typo renders the default villager rather than failing the spawn. Send this only to a villager;
     * any other type has no villager-data field at that index.
     *
     * @param type the villager-type registry name (plain or namespaced), defaulted when unknown
     * @param profession the villager-profession registry name (plain or namespaced), defaulted when unknown
     * @param level the badge level the client renders (the protocol uses 1–5)
     */
    Object villagerData(int entityId, String type, String profession, int level);

    /**
     * Build the metadata packet that sets a slime's (or magma cube's) size through the {@code ID_SIZE} field — an
     * integer where larger is bigger and the body's collision box and render scale follow it. The protocol stores
     * the size as {@code size}; the client renders a {@code size}-block-ish cube. Send this only to a slime or
     * magma cube; any other type has no size field at that index. The plugin clamps the value to a sane range
     * before calling — this builder only guards against the impossible.
     *
     * @param size the slime size (the plugin clamps it; 1 is the smallest natural slime)
     * @throws IllegalArgumentException if {@code size} is below 1, which the slime size field cannot represent
     */
    Object slimeSize(int entityId, int size);

    /**
     * Build the metadata packet that toggles a creeper's charged (powered) state through the creeper's {@code
     * DATA_IS_POWERED} field — the boolean that renders the blue electrified aura a charged creeper carries. Send
     * this only to a creeper; any other type has no powered field at that index.
     */
    Object charged(int entityId, boolean charged);

    /**
     * Build the metadata packet that sets a horse's appearance — its coat {@code color} (0–6) and its body
     * {@code markings} (0–4) — through the horse's {@code DATA_ID_TYPE_VARIANT} field. The two pack into the one
     * integer the field carries (colour in the low byte, markings in the high byte) exactly as the server's own
     * {@code setVariantAndMarkings} does; see {@link HorseVariant}. Send this only to a horse; any other type
     * (a donkey, a mule, a llama) has no such combined variant field at that index. The plugin clamps the two
     * values to their real ranges before calling.
     */
    Object horseVariant(int entityId, int color, int markings);

    /**
     * Build the metadata packet that sets a llama's coat {@code variant} (0–3) through the llama's {@code
     * DATA_VARIANT_ID} field — the integer that picks one of the four llama coats. Send this only to a llama or
     * trader llama; any other type has no llama-variant field at that index. The plugin clamps the value first.
     */
    Object llamaVariant(int entityId, int variant);

    /**
     * Build the metadata packet that sets a sheep's wool {@code color} (a {@code DyeColor} id, 0–15) in the low four
     * bits of the {@code DATA_WOOL_ID} byte and the {@code sheared} flag in bit {@code 0x10} — both composed into the
     * one byte. A sheared sheep hides its wool until it regrows. Send this only to a sheep; any other type has no
     * wool byte at that index.
     */
    Object sheepWool(int entityId, int color, boolean sheared);

    /**
     * Build the metadata packet that sets a wolf's collar {@code color} (a {@code DyeColor} id, 0–15) through the
     * wolf's {@code DATA_COLLAR_COLOR} field. Send this only to a wolf; any other type has no collar field at that
     * index.
     */
    Object wolfCollar(int entityId, int color);

    /**
     * Build the metadata packet that sets a shulker's shell {@code color} (a {@code DyeColor} id, 0–15) through the
     * shulker's {@code DATA_COLOR_ID} byte. The natural uncoloured shulker uses 16; this builder writes a 0–15 dye
     * id, so the plugin validates the colour before calling. Send this only to a shulker; any other type has no
     * colour byte at that index.
     */
    Object shulkerColor(int entityId, int color);

    /**
     * Build the metadata packet that sets how far a shulker's shell is open through its {@code DATA_PEEK_ID} byte —
     * 0 is fully closed, 100 fully open. Send this only to a shulker; any other type has no peek byte at that index.
     * The plugin clamps the value to 0–100 before calling.
     */
    Object shulkerPeek(int entityId, int peek);

    /**
     * Build the metadata packet that sets a panda's gene (its visible face and temperament) through the panda's
     * {@code MAIN_GENE_ID} and {@code HIDDEN_GENE_ID} bytes — the gene id (0–6: normal, lazy, worried, playful,
     * brown, weak, aggressive). Both genes are set to the same id so a recessive gene (brown or weak) renders its
     * own phenotype rather than falling back to normal, which the client does only when the two genes match. Send
     * this only to a panda; any other type has no gene byte at that index. The plugin validates the gene id first.
     */
    Object pandaGene(int entityId, int gene);

    /**
     * Build the metadata packet that toggles a goat's screaming variant through {@code Goat.DATA_IS_SCREAMING_GOAT}.
     * A screaming goat shares the model but rams and bleats differently. Send this only to a goat; any other type
     * has no screaming field at that index.
     */
    Object goatScreaming(int entityId, boolean screaming);

    /**
     * Build the metadata packet that toggles an allay's dance through {@code Allay.DATA_DANCING} — a dancing allay
     * bobs in place as it does beside a playing jukebox. Send this only to an allay; any other type has no dancing
     * field at that index.
     */
    Object allayDancing(int entityId, boolean dancing);

    /**
     * Build the metadata packet that toggles a piglin's dance through {@code Piglin.DATA_IS_DANCING}. Send this only
     * to a piglin; any other type has no dancing field at that index.
     */
    Object piglinDancing(int entityId, boolean dancing);

    /**
     * Build the metadata packet that toggles a camel's dash through {@code Camel.DASH} — the sprint-leap state the
     * client animates. Send this only to a camel; any other type has no dash field at that index.
     */
    Object camelDash(int entityId, boolean dashing);

    /**
     * Build the metadata packet that sets a bee's state through its {@code DATA_FLAGS_ID} byte — {@code nectar}
     * trails pollen particles, {@code rolling} plays the barrel-roll attack animation, {@code stung} marks a bee
     * that has stung. All three bits are composed into the one byte. Send this only to a bee; any other type has
     * no flags byte at that index.
     */
    Object beeFlags(int entityId, boolean nectar, boolean rolling, boolean stung);

    /**
     * Build the metadata packet that toggles a vex's charging state through its {@code DATA_FLAGS_ID} byte — a
     * charging vex renders its red, aggressive form. Only the {@code FLAG_IS_CHARGING} bit is written. Send this
     * only to a vex; any other type has no flags byte at that index.
     */
    Object vexCharging(int entityId, boolean charging);

    /**
     * Build the metadata packet that sets a tropical fish's appearance to the predefined common variant at
     * {@code variantIndex} (0-based into the server's own 22 bucketable varieties) through the fish's {@code
     * DATA_ID_TYPE_VARIANT} packed integer. The packed id is taken from the server's {@code COMMON_VARIANTS}
     * list — its own bit layout, not a copied formula — so the index simply picks a named tropical-fish pattern
     * and colour pair. The index is clamped to the list's range. Send this only to a tropical fish; any other type
     * has no such field at that index.
     */
    Object tropicalFishVariant(int entityId, int variantIndex);

    /**
     * Build the metadata packet that sets an armor stand's client flags — {@code small}, {@code showArms},
     * {@code noBasePlate}, {@code marker} — composed into the one {@code DATA_CLIENT_FLAGS} byte (each is a bit
     * mask the server applies directly). A statue NPC uses these for a small stand, visible arms (to hold items),
     * a hidden base plate, or marker mode (no hitbox). Send this only to an armor stand; any other type has no
     * client-flags byte at that index.
     */
    Object armorStandFlags(int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker);

    /**
     * Build the metadata packet that sets one of an armor stand's six poses ({@code part}) to the Euler angles
     * {@code x}/{@code y}/{@code z} (in degrees) through the matching {@code DATA_*_POSE} field. Each part is an
     * independent field, so posing a full statue is several of these packets. Send this only to an armor stand;
     * any other type has no pose fields at those indices.
     */
    Object armorStandPose(int entityId, ArmorStandPart part, float x, float y, float z);

    /**
     * Build the metadata packet that sizes an interaction entity's clickable hitbox to {@code width} × {@code
     * height} (blocks) and marks it responsive, through its {@code DATA_WIDTH_ID}/{@code DATA_HEIGHT_ID}/{@code
     * DATA_RESPONSE_ID} fields. An interaction entity is invisible; its only purpose is the hitbox, so a width or
     * height of zero leaves it unclickable — the caller passes a positive size. Send this only to an interaction
     * entity; any other type has no such fields at those indices.
     */
    Object interactionSize(int entityId, float width, float height);

    /**
     * Build the metadata packet that sets a block-display entity's shown block to {@code blockData} through its
     * {@code DATA_BLOCK_STATE_ID} field (the Bukkit block data is converted to the server's own block state). Send
     * this only to a block-display entity; any other type has no block-state field at that index.
     */
    Object blockDisplayState(int entityId, BlockData blockData);

    /**
     * Build the metadata packet that sets an item-display entity's shown item to {@code item} through its {@code
     * DATA_ITEM_STACK_ID} field (the Bukkit item is copied into the server's own item form). Send this only to an
     * item-display entity; any other type has no item field at that index.
     */
    Object itemDisplayItem(int entityId, ItemStack item);

    /**
     * Build the metadata packet that sets a text-display entity's shown text to {@code text} through its {@code
     * DATA_TEXT_ID} field (the Adventure component is converted to the server's own component form). Send this only
     * to a text-display entity; any other type has no text field at that index.
     */
    Object textDisplayText(int entityId, Component text);

    /**
     * Build the metadata packet that uniformly scales a display entity (block/item/text) to {@code scale} through
     * its {@code DATA_SCALE_ID} transform vector (set to {@code scale} on all three axes). {@code 1.0} is the
     * natural size. Send this only to a display entity; an interaction or other type has no scale vector there.
     */
    Object displayScale(int entityId, float scale);

    /**
     * Build the metadata packet that scales a display entity non-uniformly to {@code x}/{@code y}/{@code z} through
     * its {@code DATA_SCALE_ID} transform vector. The single-argument {@link #displayScale(int, float)} is the
     * uniform shorthand. Send this only to a display entity.
     */
    Object displayScale(int entityId, float x, float y, float z);

    /**
     * Build the metadata packet that offsets a display entity from its anchor by {@code x}/{@code y}/{@code z}
     * blocks through its {@code DATA_TRANSLATION_ID} transform vector (any axis may be negative). Send this only to
     * a display entity.
     */
    Object displayTranslation(int entityId, float x, float y, float z);

    /**
     * Build the metadata packet that sets a display entity's billboard (face-the-viewer) constraint through its
     * {@code DATA_BILLBOARD_RENDER_CONSTRAINTS_ID} byte: {@code 0} fixed, {@code 1} vertical, {@code 2} horizontal,
     * {@code 3} center (always faces the viewer). Send this only to a display entity.
     */
    Object displayBillboard(int entityId, byte constraint);

    /**
     * Build the metadata packet that sets a text-display entity's background to the packed {@code argb} colour
     * through its {@code DATA_BACKGROUND_COLOR_ID} field. Send this only to a text-display entity; any other type
     * has no background field at that index.
     */
    Object textDisplayBackground(int entityId, int argb);

    /**
     * Build the metadata packet that sets a text-display entity's line width (the pixel width at which its text
     * wraps) through its {@code DATA_LINE_WIDTH_ID} field. Send this only to a text-display entity; any other type
     * has no line-width field at that index. The plugin clamps to a positive value first.
     */
    Object textDisplayLineWidth(int entityId, int lineWidth);

    /**
     * Build the metadata packet that sets a parrot's {@code variant} (0–4) through the parrot's {@code
     * DATA_VARIANT_ID} field — the integer that picks one of the five parrot colours. Send this only to a
     * parrot; any other type has no parrot-variant field at that index. The plugin clamps the value first.
     */
    Object parrotVariant(int entityId, int variant);

    /**
     * Build the metadata packet that sets an axolotl's {@code variant} (0–4) through the axolotl's {@code
     * DATA_VARIANT} field — the integer that picks one of the five axolotl colours. Send this only to an
     * axolotl; any other type has no axolotl-variant field at that index. The plugin clamps the value first.
     */
    Object axolotlVariant(int entityId, int variant);

    /**
     * Build the metadata packet that sets a fox's {@code type} (0 red, 1 snow) through the fox's {@code
     * DATA_TYPE_ID} field. Send this only to a fox; any other type has no fox-type field at that index. The
     * plugin clamps the value first.
     */
    Object foxType(int entityId, int type);

    /**
     * Build the metadata packet that sets a rabbit's {@code type} through the rabbit's {@code DATA_TYPE_ID}
     * field — the integer that picks one of the six coats (0–5), with 99 the killer (toast) rabbit. Send this
     * only to a rabbit; any other type has no rabbit-type field at that index. The plugin validates the value
     * (the six coats plus 99) before calling.
     */
    Object rabbitType(int entityId, int type);

    /**
     * Build the metadata packet that sets a cat's coat {@code variant} (e.g. {@code tabby}, {@code calico},
     * {@code jellie}) through the cat's {@code DATA_VARIANT_ID} field. Unlike the integer coats above, a cat
     * variant is a dynamic-registry value: the field carries a {@code Holder<CatVariant>} resolved by name off
     * the live server's cat-variant registry, so the variant set must be reachable from a running server. The
     * name is the registry name (plain, defaulted to the {@code minecraft} namespace, or namespaced). Send this
     * only to a cat; any other type has no cat-variant field at that index.
     *
     * <p>Resolving against a live registry can fail (the server is not yet up, or the name is unknown), so this
     * returns {@code null} rather than throwing when the variant cannot be resolved; the caller drops a
     * {@code null} packet, leaving the cat on its default variant. The plugin validates the name against the
     * known set before calling, so a {@code null} here is the defensive floor rather than a routine path.
     *
     * @param name the cat-variant registry name (plain or namespaced)
     * @return the metadata packet, or {@code null} when the variant cannot be resolved off the live registry
     */
    @Nullable Object catVariant(int entityId, String name);

    /**
     * Build the metadata packet that sets a frog's {@code variant} ({@code temperate}, {@code warm}, or
     * {@code cold}) through the frog's {@code DATA_VARIANT_ID} field. Like {@link #catVariant}, a frog variant
     * is a dynamic-registry value: the field carries a {@code Holder<FrogVariant>} resolved by name off the live
     * server's frog-variant registry. The name is the registry name (plain, defaulted to the {@code minecraft}
     * namespace, or namespaced). Send this only to a frog; any other type has no frog-variant field at that index.
     *
     * <p>As with {@link #catVariant}, a registry lookup can fail, so this returns {@code null} rather than
     * throwing when the variant cannot be resolved; the caller drops a {@code null} packet.
     *
     * @param name the frog-variant registry name (plain or namespaced)
     * @return the metadata packet, or {@code null} when the variant cannot be resolved off the live registry
     */
    @Nullable Object frogVariant(int entityId, String name);

    /**
     * Build the metadata packet that sets a wolf's coat {@code variant} (e.g. {@code pale}, {@code ashen},
     * {@code striped}) through the wolf's {@code DATA_VARIANT_ID} field — a dynamic-registry {@code
     * Holder<WolfVariant>} resolved by name off the live server's wolf-variant registry, exactly like
     * {@link #catVariant}. Distinct from {@link #wolfCollar} (the collar colour). Send this only to a wolf; returns
     * {@code null} (dropped by the caller) when the variant cannot be resolved.
     */
    @Nullable Object wolfVariant(int entityId, String name);

    /**
     * Build the metadata packet that sets a chicken's {@code variant} ({@code temperate}/{@code warm}/{@code cold})
     * through its {@code DATA_VARIANT_ID} {@code Holder<ChickenVariant>}, resolved by name off the live registry
     * like {@link #catVariant}. Send this only to a chicken; returns {@code null} when unresolvable.
     */
    @Nullable Object chickenVariant(int entityId, String name);

    /**
     * Build the metadata packet that sets a cow's {@code variant} ({@code temperate}/{@code warm}/{@code cold})
     * through its {@code DATA_VARIANT_ID} {@code Holder<CowVariant>}, resolved by name off the live registry like
     * {@link #catVariant}. Send this only to a cow; returns {@code null} when unresolvable.
     */
    @Nullable Object cowVariant(int entityId, String name);

    /**
     * Build the metadata packet that sets a pig's {@code variant} ({@code temperate}/{@code warm}/{@code cold})
     * through its {@code DATA_VARIANT_ID} {@code Holder<PigVariant>}, resolved by name off the live registry like
     * {@link #catVariant}. Send this only to a pig; returns {@code null} when unresolvable.
     */
    @Nullable Object pigVariant(int entityId, String name);

    /**
     * Build the metadata packet that toggles an axolotl's play-dead state through {@code Axolotl.DATA_PLAYING_DEAD}
     * — a playing-dead axolotl floats belly-up. Send this only to an axolotl; any other type has no such field.
     */
    Object axolotlPlayingDead(int entityId, boolean playingDead);

    /**
     * Build the metadata packet that toggles a raider's (pillager/vindicator/evoker/illusioner/ravager/witch)
     * celebrating state through {@code Raider.IS_CELEBRATING} — a celebrating raider does the post-raid victory
     * dance. Send this only to a raider type; any other has no such field at that index.
     */
    Object raiderCelebrating(int entityId, boolean celebrating);

    /**
     * Build the metadata packet that toggles a tameable mob's (wolf/cat/parrot) sitting pose through {@code
     * TamableAnimal.DATA_FLAGS_ID}'s sitting bit. Only the sitting bit is written. Send this only to a tameable
     * type; any other has no such field at that index.
     */
    Object tameableSitting(int entityId, boolean sitting);

    /**
     * Build the metadata packet that sets a fox's pose flags — {@code sitting}, {@code sleeping}, {@code crouching}
     * — composed into the one {@code Fox.DATA_FLAGS_ID} byte (each is a bit mask the server applies directly). Send
     * this only to a fox; any other type has no such byte at that index.
     */
    Object foxFlags(int entityId, boolean sitting, boolean sleeping, boolean crouching);

    /**
     * Build the metadata packet that toggles a panda's eating animation through its {@code EAT_COUNTER} (a positive
     * counter shows the sitting-and-eating pose; zero stops it). Send this only to a panda.
     */
    Object pandaEating(int entityId, boolean eating);

    /**
     * Build the metadata packet that sets a sniffer's animation state (one of {@code idling}, {@code feeling_happy},
     * {@code scenting}, {@code sniffing}, {@code searching}, {@code digging}, {@code rising}; case-insensitive).
     * Returns {@code null} for an unknown name so the caller can skip fail-soft. Send this only to a sniffer.
     */
    @Nullable Object snifferState(int entityId, String state);

    /**
     * Build the metadata packet that sets an armadillo's state (one of {@code idle}, {@code rolling}, {@code scared},
     * {@code unrolling}; case-insensitive). Returns {@code null} for an unknown name so the caller can skip
     * fail-soft. Send this only to an armadillo.
     */
    @Nullable Object armadilloState(int entityId, String state);

    /**
     * Build the metadata packet that puts a living entity into the item-use pose through its {@code
     * DATA_LIVING_ENTITY_FLAGS} byte — {@code using} raises the held item (a shield blocks, food/potion lifts), and
     * {@code offHand} marks it the off-hand item. Both bits are composed into the one byte the server applies
     * directly. Send this only to a living entity (any mob/player); display/interaction entities have no such byte.
     */
    Object usingItem(int entityId, boolean using, boolean offHand);

    /**
     * Build the metadata packet that sets an entity's powder-snow freeze ticks ({@code DATA_TICKS_FROZEN}); a value
     * at or past the freeze threshold renders the blue shivering overlay, zero clears it. Applies to any entity.
     */
    Object frozenTicks(int entityId, int ticks);

    /**
     * Build the scoreboard-team packet that tints the NPC's glow to {@code color}. The client colours a glowing
     * entity's outline with the colour of the team its name is a member of, so this packet creates (or modifies) a
     * team named {@code teamName}, sets its colour, and seats {@code memberName} as a member. For a fake player
     * the member name is its profile name (the same name carried on the player-info entry, capped at 16 chars). A
     * {@code null} colour leaves the team colourless, which renders the default white outline.
     *
     * @param teamName the team name to create or modify (stable per NPC so a re-colour reuses it)
     * @param memberName the NPC's profile name, seated as the team's sole member
     * @param color the glow colour, or {@code null} for the default white outline
     */
    Object glowColor(String teamName, String memberName, @Nullable NamedColor color);

    /**
     * Build the scoreboard-team packet that removes the glow-colour team {@code teamName} from the client. A team
     * created by {@link #glowColor} is client-side state that outlives the entity — despawning the fake player does
     * not drop it — so a viewer who no longer sees the NPC, or sees it stop glowing, must be sent this to clear the
     * orphaned team (otherwise the colour can later bind to a real player who happens to share the seated name).
     * Removing a team the client never had is a harmless no-op, so this is safe to send on every despawn path.
     *
     * @param teamName the team name to remove (the same name {@link #glowColor} created)
     */
    Object glowColorRemove(String teamName);

    /** Wrap several already-built packets into one bundle so a tab-add + spawn arrives as one atomic frame. */
    Object bundle(List<Object> packets);

    /** Write {@code packet} to {@code viewer}'s connection. A no-op if the connection cannot be resolved. */
    void send(Player viewer, Object packet);
}
