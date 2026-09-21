package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Who names a diamond sword for a player who reads Turkish.
 *
 * <p>ADR 0007 said a player who has chosen a language reads every word in it, vanilla names included, out
 * of a table of ours. The table was never written, and writing one means every material, every mob and
 * every enchantment in every language we ship, maintained against the version the platform moves to.
 * Minecraft ships that table already, in the client, and the client draws it for the language the player
 * actually set.
 *
 * <p>So the rule is one sentence: our catalogue wins, the client answers for everything else. An operator
 * who wants one name of their own writes the key, and nobody has to write the other nine hundred.
 */
class VanillaNamesTest {

    @BeforeEach
    void start() {
        MockBukkit.mock();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a name nobody wrote is left to the client, in its own language")
    void anameNobodyWroteIsTheClients() {
        Component drawn = VanillaNames.of(Material.DIAMOND_SWORD, empty(), Locale.forLanguageTag("tr"));

        assertThat(drawn)
                .describedAs("a translatable component is drawn by the client in the language it is set to")
                .isInstanceOf(TranslatableComponent.class);
        assertThat(((TranslatableComponent) drawn).key()).isEqualTo(Material.DIAMOND_SWORD.translationKey());
    }

    @Test
    @DisplayName("a name the catalogue holds is ours, in the reader's language")
    void anameTheCatalogueHoldsIsOurs() {
        MessageCatalog ours = new MessageCatalog(
                Map.of(
                        Locale.forLanguageTag("tr"),
                        Map.of("vanilla.material.diamond_sword", "Elmas Kılıç"),
                        Locale.ENGLISH,
                        Map.of()),
                Locale.ENGLISH);

        Component drawn = VanillaNames.of(Material.DIAMOND_SWORD, ours, Locale.forLanguageTag("tr"));

        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(drawn))
                .isEqualTo("Elmas Kılıç");
    }

    @Test
    @DisplayName("a name written in one language serves a reader of another")
    void anameWrittenOnceServesEverybody() {
        MessageCatalog ours = new MessageCatalog(
                Map.of(Locale.ENGLISH, Map.of("vanilla.material.diamond_sword", "Sword of the House")), Locale.ENGLISH);

        Component drawn = VanillaNames.of(Material.DIAMOND_SWORD, ours, Locale.forLanguageTag("tr"));

        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(drawn))
                .describedAs("an operator who renames one material renames it for every reader, not for one"
                        + " language: the alternative is a server where the rename works in English and the"
                        + " client's own word comes back in Turkish")
                .isEqualTo("Sword of the House");
    }

    @Test
    @DisplayName("a mob and an enchantment are asked the same question")
    void amobAndAnEnchantmentAreAskedTheSameQuestion() {
        assertThat(VanillaNames.keyOf(EntityType.CREEPER)).isEqualTo("vanilla.entity.creeper");
        assertThat(VanillaNames.keyOf(Material.STONE)).isEqualTo("vanilla.material.stone");

        Component mob = VanillaNames.of(EntityType.CREEPER, empty(), Locale.ENGLISH);

        assertThat(mob).isInstanceOf(TranslatableComponent.class);
    }

    @Test
    @DisplayName("what a window writes is a tag the renderer parses, not a word this jar picked")
    void whatAWindowWritesIsATag() {
        assertThat(VanillaNames.mini(Material.STONE, empty(), Locale.ENGLISH))
                .describedAs("a menu placeholder answers with text, so the translatable arrives as a lang tag")
                .isEqualTo("<lang:'" + Material.STONE.translationKey() + "'>");

        MessageCatalog ours =
                new MessageCatalog(Map.of(Locale.ENGLISH, Map.of("vanilla.material.stone", "Rock")), Locale.ENGLISH);

        assertThat(VanillaNames.mini(Material.STONE, ours, Locale.ENGLISH)).isEqualTo("Rock");
    }

    /** A catalogue that names nothing of its own, which is every plugin until an operator writes a key. */
    private static MessageCatalog empty() {
        return new MessageCatalog(Map.of(Locale.ENGLISH, Map.of()), Locale.ENGLISH);
    }
}
