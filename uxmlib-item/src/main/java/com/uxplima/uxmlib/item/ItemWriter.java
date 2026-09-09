package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.potion.PotionEffect;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Writes a live {@link ItemStack} into a HOCON node in the vocabulary {@link ItemConfig} reads back.
 *
 * <p>The half that was missing. {@code ItemConfig} has read the full definition of an item since it was
 * written and its own javadoc said a writer could come later; without one, every plugin that takes an item
 * out of a player's hand had to invent its own subset, and every one of them invented a smaller subset than
 * the reader understands. uxmCrates stored a material, a count, a name, lore, a glint and two model fields,
 * so a Sharpness V sword handed to its reward editor was written down as a plain sword with a glint on it,
 * and an enchanted book could not be written down at all.
 *
 * <p>The rule this class keeps is one sentence: <strong>a file we write must be a file we read</strong>.
 * Every key here is a key {@code ItemConfig.load} understands, spelled the same way, so the round trip is
 * exact and adding a field to one half without the other is a test failure rather than a silent loss.
 *
 * <p>What is written is what differs from a plain stack of that material. A diamond with nothing on it
 * writes a material and a count and nothing else, so a file an operator opens is as short as what it says.
 */
public final class ItemWriter {

    private ItemWriter() {}

    /**
     * Whether {@code stack} says anything a material and a count cannot.
     *
     * <p>The question a caller asks before deciding between the shorthand {@code "DIAMOND 3"} and a block.
     */
    public static boolean isPlain(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        return !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasEnchants()
                && !meta.hasEnchantmentGlintOverride()
                && meta.getItemFlags().isEmpty()
                && !meta.isUnbreakable()
                && !meta.hasCustomModelDataComponent()
                && !meta.hasItemModel()
                && !(meta instanceof Damageable damageable && damageable.hasDamage())
                && !(meta instanceof EnchantmentStorageMeta stored && stored.hasStoredEnchants())
                && !(meta instanceof SkullMeta skull && skull.hasOwner())
                && !(meta instanceof ArmorMeta armour && armour.hasTrim())
                && !(meta instanceof PotionMeta potion && (potion.hasCustomEffects() || potion.hasBasePotionType()))
                && !(meta instanceof LeatherArmorMeta)
                && !(meta instanceof BannerMeta banner && !banner.getPatterns().isEmpty())
                && !(meta instanceof FireworkMeta firework
                        && !firework.getEffects().isEmpty())
                && !(meta instanceof BlockStateMeta);
    }

    /**
     * Write {@code stack} into {@code node}.
     *
     * <p>The name and the lore are written back as MiniMessage, because that is what the file holds and what
     * the reader parses. A name an operator typed in an anvil with no colour on it comes back as plain words.
     */
    public static void write(ItemStack stack, ConfigurationNode node) throws SerializationException {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(node, "node");
        node.node("material").set(stack.getType().name());
        if (stack.getAmount() != 1) {
            node.node("amount").set(stack.getAmount());
        }
        if (!stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        writeText(meta, node);
        writeEnchants(meta, node);
        writeScalars(meta, node);
        writeTyped(stack.getType(), meta, node);
    }

    private static void writeText(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        Component name = meta.displayName();
        if (name != null) {
            node.node("name").set(mini(name));
        }
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            List<String> lines = new ArrayList<>(lore.size());
            for (Component line : lore) {
                lines.add(mini(line));
            }
            node.node("lore").setList(String.class, lines);
        }
    }

    private static void writeEnchants(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            node.node("enchants", key(entry.getKey())).set(entry.getValue());
        }
        if (meta instanceof EnchantmentStorageMeta stored) {
            for (Map.Entry<Enchantment, Integer> entry :
                    stored.getStoredEnchants().entrySet()) {
                node.node("stored-enchants", key(entry.getKey())).set(entry.getValue());
            }
        }
    }

    /** The id the reader looks an enchantment up by: the key alone for vanilla, namespaced otherwise. */
    private static String key(Enchantment enchantment) {
        NamespacedKey named = enchantment.getKey();
        return NamespacedKey.MINECRAFT.equals(named.getNamespace()) ? named.getKey() : named.asString();
    }

    private static void writeScalars(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (!meta.getItemFlags().isEmpty()) {
            List<String> flags = new ArrayList<>();
            for (ItemFlag flag : meta.getItemFlags()) {
                flags.add(flag.name());
            }
            node.node("flags").setList(String.class, flags);
        }
        if (meta.isUnbreakable()) {
            node.node("unbreakable").set(true);
        }
        if (!meta.hasEnchants()
                && meta.hasEnchantmentGlintOverride()
                && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride())) {
            node.node("glow").set(true);
        }
        modelData(meta).ifPresent(value -> setQuietly(node.node("custom-model-data"), value));
        if (meta.hasItemModel()) {
            NamespacedKey model = meta.getItemModel();
            if (model != null) {
                setQuietly(node.node("item-model"), model.asString());
            }
        }
        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            node.node("damage").set(damageable.getDamage());
        }
    }

    /**
     * The custom model data, when the stack carries one number a file can hold.
     *
     * <p>A 1.21.4 client reads a list of floats, a list of strings, a list of flags and a list of colours,
     * and a file holds one integer, which is the shape a resource pack has used for far longer. The first
     * float is taken when it is a whole number and the rest is left alone, because inventing an integer for
     * a stack that carries three floats would write a file that draws the wrong model.
     */
    private static java.util.Optional<Integer> modelData(ItemMeta meta) {
        if (!meta.hasCustomModelDataComponent()) {
            return java.util.Optional.empty();
        }
        List<Float> floats = meta.getCustomModelDataComponent().getFloats();
        if (floats.size() != 1) {
            return java.util.Optional.empty();
        }
        float only = floats.get(0);
        return only == Math.rint(only) ? java.util.Optional.of((int) only) : java.util.Optional.empty();
    }

    private static void writeTyped(Material material, ItemMeta meta, ConfigurationNode node)
            throws SerializationException {
        writeSkull(meta, node);
        writeTrim(meta, node);
        writePotion(meta, node);
        writeLeather(meta, node);
        writeBanner(meta, node);
        writeFirework(meta, node);
        writeSpawner(material, meta, node);
    }

    private static void writeSkull(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (meta instanceof SkullMeta skull && skull.hasOwner()) {
            var owner = skull.getOwningPlayer();
            String name = owner == null ? null : owner.getName();
            if (name != null && !name.isBlank()) {
                node.node("skull").set(name);
            }
        }
    }

    private static void writeTrim(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (meta instanceof ArmorMeta armour && armour.hasTrim()) {
            ArmorTrim trim = armour.getTrim();
            if (trim != null) {
                node.node("trim", "material").set(registryKey(RegistryKey.TRIM_MATERIAL, trim.getMaterial()));
                node.node("trim", "pattern").set(registryKey(RegistryKey.TRIM_PATTERN, trim.getPattern()));
            }
        }
    }

    private static void writePotion(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (!(meta instanceof PotionMeta potion)) {
            return;
        }
        if (potion.hasBasePotionType()) {
            var base = potion.getBasePotionType();
            if (base != null) {
                node.node("potion", "type").set(base.getKey().getKey());
            }
        }
        if (potion.hasColor()) {
            Color colour = potion.getColor();
            if (colour != null) {
                node.node("potion", "color").set(hex(colour));
            }
        }
        if (potion.hasCustomEffects()) {
            List<String> written = new ArrayList<>();
            for (PotionEffect effect : potion.getCustomEffects()) {
                written.add(effect.getType().getKey().getKey().toLowerCase(Locale.ROOT) + ":" + effect.getAmplifier()
                        + ":" + effect.getDuration());
            }
            node.node("potion", "effects").setList(String.class, written);
        }
    }

    private static void writeLeather(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (meta instanceof LeatherArmorMeta leather) {
            node.node("leather-color").set(hex(leather.getColor()));
        }
    }

    private static void writeBanner(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (!(meta instanceof BannerMeta banner) || banner.getPatterns().isEmpty()) {
            return;
        }
        List<String> written = new ArrayList<>();
        for (Pattern pattern : banner.getPatterns()) {
            written.add(registryKey(RegistryKey.BANNER_PATTERN, pattern.getPattern()) + ":"
                    + pattern.getColor().name().toLowerCase(Locale.ROOT));
        }
        node.node("banner", "patterns").setList(String.class, written);
    }

    private static void writeFirework(ItemMeta meta, ConfigurationNode node) throws SerializationException {
        if (!(meta instanceof FireworkMeta firework) || firework.getEffects().isEmpty()) {
            return;
        }
        node.node("firework", "power").set(firework.getPower());
        List<String> written = new ArrayList<>();
        for (FireworkEffect effect : firework.getEffects()) {
            written.add(firework(effect));
        }
        node.node("firework", "effects").setList(String.class, written);
    }

    /** One firework effect as {@code type:colours:fade-colours:flags}, which is what the reader parses. */
    private static String firework(FireworkEffect effect) {
        StringBuilder out = new StringBuilder(effect.getType().name().toLowerCase(Locale.ROOT));
        out.append(':').append(joinColours(effect.getColors()));
        out.append(':').append(joinColours(effect.getFadeColors()));
        List<String> flags = new ArrayList<>(2);
        if (effect.hasFlicker()) {
            flags.add("flicker");
        }
        if (effect.hasTrail()) {
            flags.add("trail");
        }
        out.append(':').append(String.join(",", flags));
        return out.toString();
    }

    private static String joinColours(List<Color> colours) {
        List<String> written = new ArrayList<>(colours.size());
        for (Color colour : colours) {
            written.add(hex(colour));
        }
        return String.join(",", written);
    }

    private static void writeSpawner(Material material, ItemMeta meta, ConfigurationNode node)
            throws SerializationException {
        if (material != Material.SPAWNER || !(meta instanceof BlockStateMeta state) || !state.hasBlockState()) {
            return;
        }
        if (state.getBlockState() instanceof org.bukkit.block.CreatureSpawner spawner) {
            var type = spawner.getSpawnedType();
            if (type != null) {
                node.node("spawner").set(type.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * The id a registry entry is written down by, which is the id the reader looks it back up by.
     *
     * <p>Asked of the registry rather than of the value, because a value's own {@code getKey} is deprecated
     * for removal on this platform and the registry is where the reader goes anyway.
     */
    private static <T extends org.bukkit.Keyed> String registryKey(RegistryKey<T> registry, T value) {
        NamespacedKey named =
                RegistryAccess.registryAccess().getRegistry(registry).getKey(value);
        if (named == null) {
            throw new IllegalStateException("a registry entry with no key cannot be written: " + value);
        }
        return NamespacedKey.MINECRAFT.equals(named.getNamespace()) ? named.getKey() : named.asString();
    }

    private static String hex(Color colour) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    /** A set that cannot fail on a scalar, so an optional's consumer stays a consumer. */
    private static void setQuietly(ConfigurationNode node, Object value) {
        try {
            node.set(value);
        } catch (SerializationException impossible) {
            throw new IllegalStateException("a scalar could not be written to " + node.path(), impossible);
        }
    }

    private static String mini(Component text) {
        return MiniMessage.miniMessage().serialize(text);
    }
}
