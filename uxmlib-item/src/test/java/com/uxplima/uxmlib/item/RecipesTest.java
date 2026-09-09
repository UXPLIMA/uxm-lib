package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Turning nine words an operator wrote into a recipe the server registers.
 *
 * <p>Every product in this market lets a server make its own key, token or crate craftable, and every one of
 * them writes it as nine entries read left to right. Nothing in this library could take those nine words, so
 * a plugin that wanted the feature would have written its own pattern builder, and the second plugin that
 * wanted it would have written a slightly different one.
 *
 * <p>What is pinned here is the reading. Registration is one line of Bukkit and it is not what goes wrong;
 * what goes wrong is a pattern built with nine letters for nine identical ingots, or a typo in a material
 * name that vanishes and leaves an operator staring at an empty recipe book.
 */
class RecipesTest {

    private static final NamespacedKey KEY = NamespacedKey.fromString("uxmlib:test-key");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack result() {
        return new ItemStack(Material.TRIPWIRE_HOOK);
    }

    @Test
    @DisplayName("nine entries become the three rows a player has to fill, and an empty square stays empty")
    void nineEntriesBecomeAPattern() {
        Recipe read = Recipes.read(
                        KEY,
                        result(),
                        false,
                        List.of(
                                "air",
                                "gold_ingot",
                                "air",
                                "gold_ingot",
                                "tripwire_hook",
                                "gold_ingot",
                                "air",
                                "gold_ingot",
                                "air"))
                .orElseThrow();

        assertThat(read).isInstanceOf(ShapedRecipe.class);
        ShapedRecipe shaped = (ShapedRecipe) read;
        assertThat(shaped.getShape()).containsExactly(" a ", "aba", " a ");
    }

    /**
     * Nine gold ingots are one ingredient and not nine, because a shaped recipe holds at most nine letters
     * and a builder that spends one per square runs out on the fully filled recipe an operator is most
     * likely to write.
     */
    @Test
    @DisplayName("a square that repeats an ingredient reuses its letter rather than spending a new one")
    void arepeatedIngredientKeepsOneLetter() {
        ShapedRecipe shaped = (ShapedRecipe) Recipes.read(
                        KEY,
                        result(),
                        false,
                        List.of(
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot",
                                "gold_ingot"))
                .orElseThrow();

        assertThat(shaped.getShape()).containsExactly("aaa", "aaa", "aaa");
        assertThat(shaped.getChoiceMap()).hasSize(1);
    }

    @Test
    @DisplayName("a shapeless recipe keeps every filled square and forgets where it was")
    void ashapelessRecipeIsAListOfIngredients() {
        Recipe read = Recipes.read(KEY, result(), true, List.of("gold_ingot", "air", "gold_ingot", "diamond"))
                .orElseThrow();

        assertThat(read).isInstanceOf(ShapelessRecipe.class);
        assertThat(((ShapelessRecipe) read).getChoiceList()).hasSize(3);
    }

    /** No recipe written is the ordinary case, so it is empty rather than an exception. */
    @Test
    @DisplayName("a list that names nothing is no recipe rather than a broken one")
    void anEmptyListIsNoRecipe() {
        assertThat(Recipes.read(KEY, result(), false, List.of())).isEmpty();
        assertThat(Recipes.read(
                        KEY, result(), false, List.of("air", "air", "air", "air", "air", "air", "air", "air", "air")))
                .isEmpty();
    }

    /**
     * A typo is loud. A silent drop would leave an operator opening the recipe book over and over, and the
     * word they mistyped is the one thing that tells them where to look.
     */
    @Test
    @DisplayName("a word that names no material fails with the word in the message")
    void amistypedMaterialSaysWhichWord() {
        assertThatThrownBy(() -> Recipes.read(
                        KEY,
                        result(),
                        false,
                        List.of("air", "gold_ingut", "air", "air", "air", "air", "air", "air", "air")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gold_ingut");
    }

    @Test
    @DisplayName("a shaped recipe that is not nine entries long says so rather than guessing the shape")
    void ashapedRecipeIsNineEntries() {
        assertThatThrownBy(() -> Recipes.read(KEY, result(), false, List.of("gold_ingot", "gold_ingot")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nine");
    }

    /** An operator writes a material the way they read it, so case and stray spaces cannot matter. */
    @Test
    @DisplayName("a material is read whatever case it was written in")
    void caseDoesNotMatter() {
        assertThat(Recipes.read(KEY, result(), true, List.of(" GOLD_INGOT ", "minecraft:Diamond")))
                .isPresent();
    }
}
