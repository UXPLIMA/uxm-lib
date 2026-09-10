package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Reads an item spec from a HOCON {@link ConfigurationNode} into an {@link ItemBuilder}: the single
 * most-replicated competitor pattern, and the seam the GUI menu loader sits on. A node looks like:
 *
 * <pre>{@code
 * material = DIAMOND_SWORD            # required
 * name = "<red>Blade"                 # MiniMessage
 * lore = ["<gray>line one", "two"]    # MiniMessage; a "\n" splits one entry into several lines
 * amount = 1
 * enchants { sharpness = 5 }          # vanilla key -> level
 * flags = [HIDE_ENCHANTS]
 * custom-model-data = 42
 * unbreakable = true
 * glow = true
 * damage = 25                        # how worn a tool arrives
 * item-model = "namespace:path"      # the 1.21.4 item model
 * stored-enchants { mending = 1 }    # what an enchanted book holds, which is not what it carries
 * hide-vanilla-tooltip = true         # silence the lines the client writes under the lore
 * skull = "Notch"                     # only for PLAYER_HEAD; routed through SkullData.parse
 * }</pre>
 *
 * <p>Beyond that, the six blocks that belong to one kind of item. A shop that cannot price a potion is not a
 * shop, and every one of these was written by hand in a plugin before it was written here:
 *
 * <pre>{@code
 * potion {
 *   type    = strength                # the base potion, a registry id
 *   color   = "#00AAFF"               # the bottle tint
 *   effects = ["speed:1:600"]         # effect:amplifier:durationTicks
 * }
 * leather-color = "#A1FF33"           # dyed leather armour: hex, an "r,g,b" triple, or a dye name
 * firework {
 *   power   = 2                       # flight duration, 0 to 127
 *   effects = ["ball_large:#ff0000,#ffff00:#ffffff:flicker,trail"]
 * }                                   # type:colours:fade-colours:flags; the last two are optional
 * trim { material = diamond, pattern = sentry }
 * banner { patterns = ["stripe_top:red", "border:white"] }   # laid on in the order written
 * spawner = zombie                    # the mob inside a spawner
 * }</pre>
 *
 * <p>Those keys and tokens are the ones a menu file's {@code decor} block already uses in
 * {@code uxmlib-menu}, key for key, so a block that draws an item in a menu means the same thing here. A
 * value that names nothing the server knows throws and says which value it was: this is read once at load,
 * into an item an operator will sell, so a mistyped potion name must not quietly become a water bottle.
 *
 * <p>Name and lore pass through MiniMessage with any supplied {@link TagResolver placeholders}; lore can be
 * auto-wrapped to a width. Lives in the item module on purpose: it has no GUI dependency.
 *
 * <p>{@link ItemWriter} is the other direction, and the two share this vocabulary key for key. A plugin that
 * takes an item out of a player's hand and writes it into a file is reading its own file back on the next
 * start, so a key one half spells and the other does not is a reward that quietly loses half of itself.
 */
public final class ItemConfig {

    private ItemConfig() {}

    /**
     * Where an item this server's own material list does not know may still come from.
     *
     * <p>A custom item plugin owns items nothing else can name. Without this, a file that writes
     * {@code material = "oraxen:ruby"} fails at load with "unknown material" and the operator's only
     * remaining option is to write the block it is drawn as, which is a different item.
     *
     * <p>Static and set once, the way the placeholder hook is. It is a property of the server rather than
     * of a plugin: four of ours read item files and all four want the same answer, and threading a source
     * through every reader would be four copies of the same wiring.
     */
    @FunctionalInterface
    public interface ItemSource {

        /** The item one namespaced id names, or nothing when nothing here owns it. */
        Optional<ItemStack> byId(String id);
    }

    /** Where an unknown material is looked for, which is nowhere until something says otherwise. */
    private static volatile ItemSource items = id -> Optional.empty();

    /**
     * Say where an item this server's material list does not know comes from.
     *
     * <p>Called once, at enable, by whichever plugin of ours starts first. Calling it again replaces the
     * source, which is what a reload does.
     */
    public static void itemsFrom(ItemSource source) {
        items = Objects.requireNonNull(source, "source");
    }

    /** Forget the source, which a shutdown does so a reload does not hold the old server's plugins. */
    public static void forgetItemSource() {
        items = id -> Optional.empty();
    }

    /** Load the spec at {@code node} into a builder, with no placeholders and no lore wrapping. */
    public static ItemBuilder load(ConfigurationNode node) {
        return load(node, List.of(), 0);
    }

    /** Load the spec, resolving {@code resolvers} in the name and lore MiniMessage. */
    public static ItemBuilder load(ConfigurationNode node, List<TagResolver> resolvers) {
        return load(node, resolvers, 0);
    }

    /**
     * Load the spec, resolving {@code resolvers} and (when {@code wrapWidth > 0}) word-wrapping each lore
     * line to that visible width.
     *
     * @throws IllegalArgumentException if {@code material} is missing/unknown or any field is malformed
     */
    public static ItemBuilder load(ConfigurationNode node, List<TagResolver> resolvers, int wrapWidth) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(resolvers, "resolvers");
        if (wrapWidth < 0) {
            throw new IllegalArgumentException("wrapWidth must be >= 0");
        }
        TagResolver[] tags = resolvers.toArray(TagResolver[]::new);
        ItemBuilder builder = base(node);
        applyAmount(node, builder);
        applyName(node, builder, tags);
        applyLore(node, builder, tags, wrapWidth);
        applyEnchants(node, builder);
        applyStoredEnchants(node, builder);
        applyFlags(node, builder);
        applyScalars(node, builder);
        applySkull(node, builder);
        ItemConfigMeta.apply(node, builder);
        return builder;
    }

    /**
     * What the spec starts from: a material this server knows, or an item another plugin owns.
     *
     * <p>An id with a namespace that is not {@code minecraft} is asked of the item source first, and only
     * a source that answers nothing falls through to the material list. Everything else in the spec is then
     * applied on top, so a file may take a vendor's item and rename it, re-lore it or enchant it exactly as
     * it may with an ordinary one.
     */
    private static ItemBuilder base(ConfigurationNode node) {
        String raw = node.node("material").getString();
        if (raw != null
                && raw.indexOf(':') >= 0
                && !raw.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            Optional<ItemStack> owned = items.byId(raw.trim());
            if (owned.isPresent()) {
                return ItemBuilder.from(owned.get());
            }
        }
        return ItemBuilder.of(material(node));
    }

    private static Material material(ConfigurationNode node) {
        String raw = node.node("material").getString();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("item config requires a 'material'");
        }
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new IllegalArgumentException("unknown material: " + raw);
        }
        return material;
    }

    private static void applyAmount(ConfigurationNode node, ItemBuilder builder) {
        int amount = node.node("amount").getInt(1);
        if (amount != 1) {
            builder.amount(amount);
        }
    }

    private static void applyName(ConfigurationNode node, ItemBuilder builder, TagResolver[] tags) {
        String name = node.node("name").getString();
        if (name != null && !name.isEmpty()) {
            builder.name(Text.mini(name, tags));
        }
    }

    private static void applyLore(ConfigurationNode node, ItemBuilder builder, TagResolver[] tags, int wrapWidth) {
        List<String> raw = stringList(node.node("lore"), "lore");
        if (raw.isEmpty()) {
            return;
        }
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        for (String entry : raw) {
            for (String wrapped : wrapWidth > 0
                    ? LoreWrap.wrap(entry, wrapWidth)
                    : entry.lines().toList()) {
                lines.add(Text.mini(wrapped, tags));
            }
        }
        builder.lore(lines);
    }

    private static void applyEnchants(ConfigurationNode node, ItemBuilder builder) {
        for (var entry : node.node("enchants").childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            int level = entry.getValue().getInt(-1);
            if (level < 1) {
                throw new IllegalArgumentException("enchant '" + key + "' needs a level >= 1");
            }
            builder.enchant(enchantment(key), level);
        }
    }

    private static org.bukkit.enchantments.Enchantment enchantment(String key) {
        try {
            return Items.enchantment(key.toLowerCase(Locale.ROOT));
        } catch (RuntimeException unknown) {
            throw new IllegalArgumentException("unknown enchant: " + key, unknown);
        }
    }

    private static void applyFlags(ConfigurationNode node, ItemBuilder builder) {
        List<String> raw = stringList(node.node("flags"), "flags");
        if (raw.isEmpty()) {
            return;
        }
        List<ItemFlag> flags = new ArrayList<>(raw.size());
        for (String name : raw) {
            flags.add(parseFlag(name));
        }
        builder.flags(flags.toArray(ItemFlag[]::new));
    }

    private static ItemFlag parseFlag(String name) {
        try {
            return ItemFlag.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown item flag: " + name, unknown);
        }
    }

    /**
     * The enchantments an enchanted book stores rather than carries.
     *
     * <p>Written apart from {@code enchants} because they are a different thing to the client: a book with
     * {@code enchants} is a glowing book that does nothing, and a book with {@code stored-enchants} is the
     * book a player puts on an anvil. An enchanted book is one of the two most common rewards a crate gives
     * and it could not be written down at all before this.
     */
    private static void applyStoredEnchants(ConfigurationNode node, ItemBuilder builder) {
        for (var entry : node.node("stored-enchants").childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            int level = entry.getValue().getInt(-1);
            if (level < 1) {
                throw new IllegalArgumentException("stored enchant '" + key + "' needs a level >= 1");
            }
            builder.storedEnchant(enchantment(key), level);
        }
    }

    private static void applyScalars(ConfigurationNode node, ItemBuilder builder) {
        ConfigurationNode cmd = node.node("custom-model-data");
        if (!cmd.virtual()) {
            builder.customModelData(cmd.getInt());
        }
        ConfigurationNode damage = node.node("damage");
        if (!damage.virtual() && damage.getInt(0) > 0) {
            builder.damage(damage.getInt());
        }
        String itemModel = node.node("item-model").getString();
        if (itemModel != null && !itemModel.isBlank()) {
            builder.itemModel(org.bukkit.NamespacedKey.fromString(itemModel));
        }
        if (node.node("unbreakable").getBoolean(false)) {
            builder.unbreakable(true);
        }
        if (node.node("glow").getBoolean(false)) {
            builder.glow(true);
        }
        if (node.node("hide-vanilla-tooltip").getBoolean(false)) {
            builder.vanillaTooltip(false);
        }
    }

    private static void applySkull(ConfigurationNode node, ItemBuilder builder) {
        String skull = node.node("skull").getString();
        if (skull != null && !skull.isBlank()) {
            builder.skull(SkullData.parse(skull));
        }
    }

    static List<String> stringList(ConfigurationNode node, String where) {
        try {
            List<String> value = node.getList(String.class);
            return value == null ? List.of() : value;
        } catch (SerializationException malformed) {
            throw new IllegalArgumentException("'" + where + "' must be a list of strings", malformed);
        }
    }
}
