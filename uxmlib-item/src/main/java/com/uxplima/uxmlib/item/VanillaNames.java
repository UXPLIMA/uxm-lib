package com.uxplima.uxmlib.item;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * The name of a vanilla thing, in the language the reader reads.
 *
 * <p>A material, a mob and an enchantment have no name of ours. Minecraft ships the table, in the client,
 * in every language the client has, and it is right for every player whose client is set to the language
 * they read. So the rule is one sentence: <strong>our catalogue wins, and the client answers for
 * everything else.</strong>
 *
 * <p>An operator who wants one name of their own writes the key for that one thing, and nobody has to
 * write the other nine hundred. The keys are {@code vanilla.material.<name>}, {@code vanilla.entity.<name>}
 * and {@code vanilla.enchantment.<name>}, all lower case, so a file reads as the table it is.
 *
 * <p>Two ways out, because a plugin writes a name in two places. {@link #of} builds the component a chat
 * line or a lore line inserts. {@link #mini} answers a menu {@code %token%}, which is text the renderer
 * parses, so the client's own name arrives as a {@code <lang:...>} tag rather than as a component the
 * token could not carry.
 *
 * <p>This replaces what ADR 0007 asked for and nobody built: a table of ours in every language we ship,
 * maintained against the version the platform moves to. The ADR says so now.
 */
public final class VanillaNames {

    /** Where a catalogue names a material of its own. */
    public static final String MATERIAL = "vanilla.material.";

    /** Where a catalogue names a mob of its own. */
    public static final String ENTITY = "vanilla.entity.";

    /** Where a catalogue names an enchantment of its own. */
    public static final String ENCHANTMENT = "vanilla.enchantment.";

    private VanillaNames() {}

    /** The key a catalogue may hold to name this material itself. */
    public static String keyOf(Material material) {
        Objects.requireNonNull(material, "material");
        return MATERIAL + material.name().toLowerCase(Locale.ROOT);
    }

    /** The key a catalogue may hold to name this mob itself. */
    public static String keyOf(EntityType entity) {
        Objects.requireNonNull(entity, "entity");
        return ENTITY + entity.name().toLowerCase(Locale.ROOT);
    }

    /** The key a catalogue may hold to name this enchantment itself. */
    public static String keyOf(Enchantment enchantment) {
        Objects.requireNonNull(enchantment, "enchantment");
        return ENCHANTMENT + enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    /** The name of this material for a reader of {@code locale}: ours when we have one, the client's otherwise. */
    public static Component of(Material material, MessageCatalog catalogue, Locale locale) {
        return named(keyOf(material), material.translationKey(), catalogue, locale);
    }

    /** The name of this mob for a reader of {@code locale}. */
    public static Component of(EntityType entity, MessageCatalog catalogue, Locale locale) {
        return named(keyOf(entity), entity.translationKey(), catalogue, locale);
    }

    /**
     * The name of this enchantment for a reader of {@code locale}.
     *
     * <p>An enchantment carries its own drawn name rather than a translation key: {@code description()} is
     * what the client would show, already a component and already translatable, and the key behind it is
     * deprecated for removal. So ours wins as everywhere else, and the fall through hands back what the
     * platform hands us.
     */
    public static Component of(Enchantment enchantment, MessageCatalog catalogue, Locale locale) {
        Objects.requireNonNull(enchantment, "enchantment");
        return ours(keyOf(enchantment), catalogue, locale)
                .map(com.uxplima.uxmlib.text.Text::mini)
                .orElseGet(enchantment::description);
    }

    /** What a menu {@code %token%} answers with for this material: our line, or a tag the client draws. */
    public static String mini(Material material, MessageCatalog catalogue, Locale locale) {
        return written(keyOf(material), material.translationKey(), catalogue, locale);
    }

    /** What a menu {@code %token%} answers with for this mob. */
    public static String mini(EntityType entity, MessageCatalog catalogue, Locale locale) {
        return written(keyOf(entity), entity.translationKey(), catalogue, locale);
    }

    /**
     * What a menu {@code %token%} answers with for this enchantment.
     *
     * <p>A token carries text, and an enchantment's own name is a component, so the fall through is the
     * plain text of it. That is the one name here a client does not draw itself, and it is the platform's
     * wording rather than ours.
     */
    public static String mini(Enchantment enchantment, MessageCatalog catalogue, Locale locale) {
        Objects.requireNonNull(enchantment, "enchantment");
        return ours(keyOf(enchantment), catalogue, locale)
                .orElseGet(() -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(enchantment.description()));
    }

    /**
     * Our line when the catalogue holds one, and the client's own translation when it does not.
     *
     * <p>{@link MessageCatalog#find} is the question, because it answers only for a line somebody wrote:
     * {@code template} would hand back the key's own default and there is no default to write here.
     */
    private static Component named(String key, String translation, MessageCatalog catalogue, Locale locale) {
        Objects.requireNonNull(catalogue, "catalogue");
        Objects.requireNonNull(locale, "locale");
        return ours(key, catalogue, locale)
                .map(com.uxplima.uxmlib.text.Text::mini)
                .orElseGet(() -> Component.translatable(translation));
    }

    /** The same answer as text, for the one seam that cannot carry a component. */
    private static String written(String key, String translation, MessageCatalog catalogue, Locale locale) {
        Objects.requireNonNull(catalogue, "catalogue");
        Objects.requireNonNull(locale, "locale");
        return ours(key, catalogue, locale).orElseGet(() -> "<lang:'" + translation + "'>");
    }

    /**
     * The line the catalogue holds for this key, in the reader's language or in the default one.
     *
     * <p>{@link MessageCatalog#find} is the question rather than {@code template}, because {@code template}
     * falls through to the key's own built-in default and there is no default to write here: the whole
     * point is that the client answers when we have not. The default locale is asked second, so an operator
     * who renames one material in their own file renames it for every reader rather than for one language.
     */
    private static Optional<String> ours(String key, MessageCatalog catalogue, Locale locale) {
        Vanilla path = new Vanilla(key);
        Optional<String> read = catalogue.find(path, locale);
        return read.isPresent() ? read : catalogue.find(path, catalogue.defaultLocale());
    }

    /**
     * One of these keys, as the catalogue asks for it.
     *
     * <p>A {@link MessageKey} carries a built-in default and these have none: a name nobody wrote is the
     * client's to draw, so the default here is the path, which nothing reads. {@code MessageKey.of} refuses
     * a blank one, and rightly: every other key in the estate has a sentence behind it.
     */
    private record Vanilla(String path) implements MessageKey {

        @Override
        public String defaultTemplate() {
            return path;
        }
    }
}
