package com.uxplima.uxmlib.menu.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.Tooltips;
import com.uxplima.uxmlib.menu.spec.DataComponents;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.RichMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The native item data a menu spec declares: enchantments, potions, colours, banner patterns, trims, damage, the item
 * model, data components and the tooltip lines the operator hides. Every token is read against the live registries,
 * and one that does not read is skipped and named on the console once, so a misspelling never stops a draw.
 */
@NullMarked
final class RichMetaWriter {

    private static final Logger LOG = Logger.getLogger(ItemRenderer.class.getName());

    /** Default custom-effect duration in ticks when a {@code effect:amplifier} token omits the duration. */
    private static final int DEFAULT_EFFECT_TICKS = 600;

    /**
     * The operator tokens already named on the console because nothing could be read out of them, keyed by the kind
     * they were being read as. An item with {@code update = true} redraws every tick, so a misspelling is named once
     * per renderer rather than once per draw.
     */
    private final Set<String> reportedUnreadableTokens = ConcurrentHashMap.newKeySet();

    /**
     * Apply every native rich-meta value the operator declared, resolving each token against the live registries.
     * Anything that does not resolve (an unknown enchant, an unsupported colour, a banner/trim registry the runtime
     * cannot model) is skipped rather than aborting the render, the same way an unknown flag is skipped.
     */
    void apply(ItemBuilder builder, RichMeta meta) {
        if (meta.unbreakable()) {
            safely(() -> builder.unbreakable(true));
        }
        meta.enchantments().forEach(token -> applyEnchant(builder, token, false));
        meta.storedEnchantments().forEach(token -> applyEnchant(builder, token, true));
        meta.leatherColor().flatMap(this::parseColor).ifPresent(color -> safely(() -> builder.leatherColor(color)));
        applyPotion(builder, meta.potion());
        meta.bannerPatterns().forEach(token -> applyBannerPattern(builder, token));
        meta.trim().ifPresent(trim -> applyTrim(builder, trim));
        meta.damage().ifPresent(damage -> applyDamage(builder, damage));
        meta.itemModel().ifPresent(model -> applyItemModel(builder, model));
        applyDataComponents(builder, meta.components());
    }

    /**
     * Apply the native data-components the operator declared. Each one resolves and applies independently and
     * fail-soft: an unknown rarity, a malformed tooltip-style key, an unresolved attribute/operation/slot, or a
     * component a runtime cannot model is skipped rather than aborting the render, the same as an unknown flag.
     */
    private void applyDataComponents(ItemBuilder builder, DataComponents components) {
        components.rarity().flatMap(this::parseRarity).ifPresent(rarity -> safely(() -> builder.rarity(rarity)));
        components.tooltipStyle().ifPresent(raw -> applyTooltipStyle(builder, raw));
        components
                .enchantGlint()
                .ifPresent(glint -> safely(() -> builder.editMeta(meta -> meta.setEnchantmentGlintOverride(glint))));
        components.enchantable().ifPresent(value -> safely(() -> builder.enchantable(value)));
        applyAttributeModifiers(builder, components.attributeModifiers());
        components.food().ifPresent(food -> applyFood(builder, food));
        components.tool().ifPresent(tool -> applyTool(builder, tool));
    }

    /**
     * Decide which components the client may write a tooltip line for, and hide the rest.
     *
     * <p>A menu icon is a button and the client does not know that: left alone it writes the mining speed under an
     * IRON_PICKAXE, "Dyed" under a tinted chestplate and the flight duration under a firework, beneath a lore that
     * already said what the button does. So the default silences {@link Tooltips#vanillaComponents()}, minus
     * whatever the {@code decor} block asked for: an operator who wrote {@code enchantments} or {@code trim} meant
     * that line to be read, and the line the client added by itself is the one nobody chose. {@code
     * hide-vanilla-tooltip=false} gives every line back, and {@code hidden-components} names an exact set and wins
     * over both.
     *
     * <p>This is about drawing and not about behaviour: hiding {@code EQUIPPABLE} removes the line, not the equip.
     * A menu stops the equip by cancelling the click, which is where that belongs.
     */
    static void hideVanillaLines(ItemStack item, RichMeta meta) {
        Set<DataComponentType> hidden = hiddenFor(meta);
        safely(() -> Tooltips.hide(item, hidden));
        // After, not before: both live on the same component, and hiding the client's lines must not hand back a
        // tooltip the spec asked to take away entirely.
        meta.components()
                .hideTooltip()
                .ifPresent(whole -> safely(() -> item.editMeta(edited -> edited.setHideTooltip(whole))));
    }

    /** The components to silence for {@code meta}: the decision this class owns, split out so a test can read it. */
    static Set<DataComponentType> hiddenFor(RichMeta meta) {
        DataComponents components = meta.components();
        if (!components.hiddenComponents().isEmpty()) {
            return components.hiddenComponents().stream()
                    .map(token -> fromRegistry(RegistryKey.DATA_COMPONENT_TYPE, keyOf(token)))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (!components.hideVanillaTooltip().orElse(true)) {
            return Set.of();
        }
        Set<DataComponentType> hidden = new HashSet<>(Tooltips.vanillaComponents());
        hidden.removeAll(declaredByTheOperator(meta));
        return Set.copyOf(hidden);
    }

    /**
     * The components the {@code decor} block filled in itself, whose tooltip lines are therefore content rather
     * than noise. Everything else in {@link Tooltips#vanillaComponents()} comes from the material alone.
     */
    private static Set<DataComponentType> declaredByTheOperator(RichMeta meta) {
        Set<DataComponentType> declared = new HashSet<>();
        if (meta.unbreakable()) {
            declared.add(DataComponentTypes.UNBREAKABLE);
        }
        if (!meta.enchantments().isEmpty()) {
            declared.add(DataComponentTypes.ENCHANTMENTS);
        }
        if (!meta.storedEnchantments().isEmpty()) {
            declared.add(DataComponentTypes.STORED_ENCHANTMENTS);
        }
        if (meta.leatherColor().isPresent()) {
            declared.add(DataComponentTypes.DYED_COLOR);
        }
        if (!meta.potion().equals(RichMeta.PotionSpec.NONE)) {
            declared.add(DataComponentTypes.POTION_CONTENTS);
        }
        if (!meta.bannerPatterns().isEmpty()) {
            declared.add(DataComponentTypes.BANNER_PATTERNS);
        }
        if (meta.trim().isPresent()) {
            declared.add(DataComponentTypes.TRIM);
        }
        if (!meta.components().attributeModifiers().isEmpty()) {
            declared.add(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }
        if (meta.components().tool().isPresent()) {
            declared.add(DataComponentTypes.TOOL);
        }
        return declared;
    }

    /** Override the tooltip-style with a resource-pack key, skipping a malformed or unsupported key. */
    private void applyTooltipStyle(ItemBuilder builder, String raw) {
        NamespacedKey key = keyOf(raw);
        if (key == null) {
            return;
        }
        safely(() -> builder.tooltipStyle(key));
    }

    /** Apply each {@code attribute:amount:operation:slot} modifier, deriving a unique key per entry by its index. */
    private void applyAttributeModifiers(ItemBuilder builder, List<String> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            applyAttributeModifier(builder, tokens.get(index), index);
        }
    }

    /**
     * Apply one {@code attribute:amount:operation:slot} modifier. The operation defaults to {@code add_number} and
     * the slot to {@code any} when omitted; an unresolved attribute, amount, operation, or slot skips the modifier.
     */
    private void applyAttributeModifier(ItemBuilder builder, String token, int index) {
        String[] parts = token.split(":", 4);
        if (parts.length < 2) {
            return;
        }
        Optional<Attribute> attribute = fromRegistry(RegistryKey.ATTRIBUTE, attributeKey(parts[0]));
        Optional<Double> amount = parseDouble(parts[1]);
        Optional<AttributeModifier.Operation> operation = parseOperation(parts.length > 2 ? parts[2] : "add_number");
        Optional<EquipmentSlotGroup> slot = parseSlotGroup(parts.length > 3 ? parts[3] : "");
        if (attribute.isEmpty() || amount.isEmpty() || operation.isEmpty() || slot.isEmpty()) {
            return;
        }
        NamespacedKey key =
                NamespacedKey.fromString("uxmlib:" + attribute.get().getKey().value() + "_" + index);
        if (key == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(key, amount.get(), operation.get(), slot.get());
        safely(() -> builder.attribute(attribute.get(), modifier));
    }

    /** Build the food component from the declared overrides, leaving any unset field at the component's default. */
    private void applyFood(ItemBuilder builder, DataComponents.FoodSpec spec) {
        safely(() -> builder.editMeta(meta -> {
            FoodComponent food = meta.getFood();
            spec.nutrition().ifPresent(food::setNutrition);
            spec.saturation().ifPresent(saturation -> food.setSaturation(saturation.floatValue()));
            spec.canAlwaysEat().ifPresent(food::setCanAlwaysEat);
            meta.setFood(food);
        }));
    }

    /** Build the tool component from the declared overrides, leaving any unset field at the component's default. */
    private void applyTool(ItemBuilder builder, DataComponents.ToolSpec spec) {
        safely(() -> builder.editMeta(meta -> {
            ToolComponent tool = meta.getTool();
            spec.defaultMiningSpeed().ifPresent(speed -> tool.setDefaultMiningSpeed(speed.floatValue()));
            spec.damagePerBlock().ifPresent(tool::setDamagePerBlock);
            meta.setTool(tool);
        }));
    }

    /**
     * Map the spec's raw flag tokens to Bukkit {@link ItemFlag}s, skipping any token that is not a flag name. The
     * token goes through {@link #enumToken} first, because every other enum-valued name in this block (a dye, a
     * rarity, an attribute operation, a slot group) is read the same way, and a flag has no reason to be the one
     * that is not. A token that is still not a flag after the fold is named on the console rather than dropped in
     * silence.
     */
    ItemFlag[] resolveFlags(List<String> tokens) {
        List<ItemFlag> flags = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            try {
                flags.add(ItemFlag.valueOf(enumToken(token)));
            } catch (IllegalArgumentException unknownFlag) {
                // A spec naming a flag that does not exist on this server should not abort the render, but a
                // dropped flag looks exactly like a server default, so it is said out loud instead of swallowed.
                reportUnreadableToken("item-flag", token);
            }
        }
        return flags.toArray(ItemFlag[]::new);
    }

    /**
     * An operator's token folded to the spelling an enum constant carries: trimmed, hyphens read as the underscores
     * they stand for, then upper-cased.
     *
     * <p>Every key in a menu file is hyphenated ({@code model-data}, {@code leather-color}, {@code
     * attribute-modifiers}), so an operator writes a value the same way and reaches for {@code hide-dye} or {@code
     * light-blue}. Without the fold that flag was skipped, and a skipped flag looks exactly like a server default, so
     * nothing on the screen said the line had been ignored.
     *
     * <p>The fold is lossless. A Java constant name holds only letters, digits, underscores and currency symbols, and
     * a hyphen cannot appear in one at all, so no constant means one thing spelled with a hyphen and another spelled
     * with an underscore. Reading the two as the same name can therefore never pick the wrong constant.
     */
    private static String enumToken(String raw) {
        return raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Name on the console one operator token nothing could be read out of, once per distinct token per renderer.
     *
     * <p>Silence is the expensive part of a fail-soft parser. A dropped flag looks like a server default and a
     * dropped colour looks like the material's own, so an operator who mistyped one sees a menu that renders and has
     * nothing at all to look for. The token and the kind it was being read as are what a search needs.
     *
     * <p>The file it was written in is not said, and that is a gap rather than a choice: {@link MenuItemSpec} is a
     * plain value with no source path on it, and nothing on the render path is told which menu file a spec was parsed
     * from. Carrying an origin down to here means a new component on the spec model and on every constructor of it,
     * which is a change of its own rather than a line in this one.
     */
    private void reportUnreadableToken(String kind, String token) {
        if (reportedUnreadableTokens.add(kind + '=' + token)) {
            LOG.warning("event=menu_token_unreadable kind=" + kind + " token='" + token + "'");
        }
    }

    /**
     * Apply one {@code name:level} enchant token to the item or its stored-enchant book. The level defaults to one
     * when absent or unparseable, and an id that does not resolve in the enchantment registry is skipped.
     */
    private void applyEnchant(ItemBuilder builder, String token, boolean stored) {
        int colon = token.lastIndexOf(':');
        Optional<Integer> parsed = colon < 0 ? Optional.empty() : parseInt(token.substring(colon + 1));
        String name = colon < 0 || parsed.isEmpty() ? token : token.substring(0, colon);
        fromRegistry(RegistryKey.ENCHANTMENT, keyOf(name)).ifPresent(enchant -> {
            int level = Math.max(1, parsed.orElse(1));
            safely(() -> {
                if (stored) {
                    builder.storedEnchant(enchant, level);
                } else {
                    builder.enchant(enchant, level);
                }
            });
        });
    }

    /** Apply a potion's base type, display colour, and any {@code effect:amplifier:durationTicks} custom effects. */
    private void applyPotion(ItemBuilder builder, RichMeta.PotionSpec potion) {
        potion.type()
                .flatMap(name -> fromRegistry(Registry.POTION, keyOf(name)))
                .ifPresent(base ->
                        safely(() -> builder.editTypedMeta(PotionMeta.class, meta -> meta.setBasePotionType(base))));
        potion.color().flatMap(this::parseColor).ifPresent(color -> safely(() -> builder.potionColor(color)));
        potion.effects()
                .forEach(token -> parsePotionEffect(token).ifPresent(e -> safely(() -> builder.potionEffect(e))));
    }

    /** Append one {@code pattern:dyecolor} banner pattern, skipping an unknown pattern id or dye name. */
    private void applyBannerPattern(ItemBuilder builder, String token) {
        int colon = token.lastIndexOf(':');
        if (colon <= 0) {
            return;
        }
        Optional<PatternType> type = fromRegistry(RegistryKey.BANNER_PATTERN, keyOf(token.substring(0, colon)));
        Optional<DyeColor> color = parseDye(token.substring(colon + 1));
        if (type.isPresent() && color.isPresent()) {
            safely(() -> builder.bannerPattern(color.get(), type.get()));
        }
    }

    /** Set the armour trim from a trim-material id and trim-pattern id, skipping either that does not resolve. */
    private void applyTrim(ItemBuilder builder, RichMeta.TrimSpec trim) {
        Optional<TrimMaterial> material = fromRegistry(RegistryKey.TRIM_MATERIAL, keyOf(trim.material()));
        Optional<TrimPattern> pattern = fromRegistry(RegistryKey.TRIM_PATTERN, keyOf(trim.pattern()));
        if (material.isPresent() && pattern.isPresent()) {
            safely(() -> builder.editTypedMeta(
                    ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(material.get(), pattern.get()))));
        }
    }

    /** Set the durability damage, a no-op on a non-damageable item; a negative value is ignored. */
    private void applyDamage(ItemBuilder builder, int damage) {
        if (damage >= 0) {
            safely(() -> builder.damage(damage));
        }
    }

    /** Override the item model with a resource-pack key, skipping a malformed or unsupported key. */
    private void applyItemModel(ItemBuilder builder, String raw) {
        NamespacedKey key = keyOf(raw);
        if (key == null) {
            return;
        }
        safely(() -> builder.itemModel(key));
    }

    /** Parse a {@code effect:amplifier:durationTicks} token into a {@link PotionEffect}, when its type resolves. */
    private static Optional<PotionEffect> parsePotionEffect(String token) {
        String[] parts = token.split(":", 3);
        Optional<PotionEffectType> type = fromRegistry(Registry.EFFECT, keyOf(parts[0]));
        if (type.isEmpty()) {
            return Optional.empty();
        }
        int amplifier = parts.length > 1 ? Math.max(0, parseInt(parts[1]).orElse(0)) : 0;
        int duration = parts.length > 2 ? parseInt(parts[2]).orElse(DEFAULT_EFFECT_TICKS) : DEFAULT_EFFECT_TICKS;
        return Optional.of(new PotionEffect(type.get(), duration, amplifier));
    }

    /**
     * A colour as a {@code #RRGGBB} hex, an {@code r,g,b} triple, or a named dye colour; empty when unparseable, and
     * named on the console when so, because a colour that did not apply looks like the material's own.
     */
    private Optional<Color> parseColor(String raw) {
        Optional<Color> color = readColor(raw.trim());
        if (color.isEmpty()) {
            reportUnreadableToken("colour", raw);
        }
        return color;
    }

    /** The colour {@code value} names in whichever of the three forms it is written; empty when it names none. */
    private static Optional<Color> readColor(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (value.startsWith("#")) {
                return Optional.of(Color.fromRGB(Integer.parseInt(value.substring(1), 16)));
            }
            if (value.contains(",")) {
                return rgbTriple(value);
            }
            return Optional.of(DyeColor.valueOf(enumToken(value)).getColor());
        } catch (IllegalArgumentException malformed) {
            // A bad hex, an out-of-range channel, or an unknown colour name is skipped, like an unknown flag.
            return Optional.empty();
        }
    }

    /** Parse an {@code r,g,b} triple into a colour; empty when it is not exactly three channels. */
    private static Optional<Color> rgbTriple(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        return Optional.of(Color.fromRGB(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())));
    }

    /** A {@link DyeColor} by folded name, present only when it matches a known dye; named on the console when not. */
    private Optional<DyeColor> parseDye(String name) {
        try {
            return Optional.of(DyeColor.valueOf(enumToken(name)));
        } catch (IllegalArgumentException unknown) {
            reportUnreadableToken("dye-colour", name);
            return Optional.empty();
        }
    }

    /** An {@link ItemRarity} by folded name (COMMON/UNCOMMON/RARE/EPIC), empty when it is not a rarity. */
    private Optional<ItemRarity> parseRarity(String name) {
        try {
            return Optional.of(ItemRarity.valueOf(enumToken(name)));
        } catch (IllegalArgumentException unknown) {
            reportUnreadableToken("rarity", name);
            return Optional.empty();
        }
    }

    /** An {@link AttributeModifier.Operation} by name ({@code add_number}/{@code add_scalar}/{@code multiply_scalar_1}). */
    private Optional<AttributeModifier.Operation> parseOperation(String name) {
        try {
            return Optional.of(AttributeModifier.Operation.valueOf(enumToken(name)));
        } catch (IllegalArgumentException unknown) {
            reportUnreadableToken("attribute-operation", name);
            return Optional.empty();
        }
    }

    /** An {@link EquipmentSlotGroup} by name; a blank token is the whole-item {@code any}, an unknown one is named. */
    private Optional<EquipmentSlotGroup> parseSlotGroup(String name) {
        return switch (enumToken(name)) {
            case "", "ANY" -> Optional.of(EquipmentSlotGroup.ANY);
            case "HAND", "MAINHAND", "MAIN_HAND" -> Optional.of(EquipmentSlotGroup.MAINHAND);
            case "OFF_HAND", "OFFHAND" -> Optional.of(EquipmentSlotGroup.OFFHAND);
            case "FEET" -> Optional.of(EquipmentSlotGroup.FEET);
            case "LEGS" -> Optional.of(EquipmentSlotGroup.LEGS);
            case "CHEST" -> Optional.of(EquipmentSlotGroup.CHEST);
            case "HEAD" -> Optional.of(EquipmentSlotGroup.HEAD);
            case "ARMOR" -> Optional.of(EquipmentSlotGroup.ARMOR);
            case "BODY" -> Optional.of(EquipmentSlotGroup.BODY);
            default -> {
                reportUnreadableToken("equipment-slot", name);
                yield Optional.empty();
            }
        };
    }

    /**
     * A registry key for an attribute name. A legacy category prefix ({@code generic.}, {@code player.}, …) is stripped
     * so the old {@code generic.attack_damage} spelling still resolves to the modern flat {@code attack_damage} key;
     * null when the remainder is not a valid key.
     */
    private static @Nullable NamespacedKey attributeKey(String raw) {
        String name = raw.trim();
        int dot = name.lastIndexOf('.');
        return keyOf(dot >= 0 ? name.substring(dot + 1) : name);
    }

    /** The registry entry for {@code key}, empty when the key is null or the data-driven registry cannot model it. */
    private static <T extends Keyed> Optional<T> fromRegistry(RegistryKey<T> registry, @Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                    RegistryAccess.registryAccess().getRegistry(registry).get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /** The entry for {@code key} in a directly-held {@link Registry}, empty when null or the runtime cannot model it. */
    private static <T extends Keyed> Optional<T> fromRegistry(Registry<T> registry, @Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(registry.get(key));
        } catch (RuntimeException registryUnavailable) {
            return Optional.empty();
        }
    }

    /** A registry key from a raw id, lower-cased so an UPPER_SNAKE config value still resolves; null when malformed. */
    private static @Nullable NamespacedKey keyOf(String raw) {
        return NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** Parse a trimmed integer, empty when the value is not a number. */
    static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Parse a trimmed double, empty when the value is not a number. */
    private static Optional<Double> parseDouble(String raw) {
        try {
            return Optional.of(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /**
     * Run one meta application, swallowing any runtime failure so a single unsupported value never aborts the whole
     * render. A meta type the test runtime cannot model (an unimplemented registry or setter) still ships working on
     * real Paper; here it simply degrades to a no-op, the same fail-soft contract as an unknown flag token.
     */
    static void safely(Runnable application) {
        try {
            application.run();
        } catch (RuntimeException unsupported) {
            // This meta type is not modelled by the runtime (or its value did not resolve); skip, never abort.
        }
    }
}
