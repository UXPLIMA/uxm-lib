package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * Registering a crafting recipe an operator wrote, and taking it back.
 *
 * <p>A mechanism rather than a game: a plugin that wants a key, a flare or a compass to be craftable says
 * what goes in and what comes out, and this turns that into the recipe the server registers and the vanilla
 * recipe book shows. What the item is for is the plugin's business and never this class's.
 *
 * <p>The written form is the one every product in this market already uses, so an operator arriving from one
 * of them recognises it: nine entries read left to right and top to bottom for a shaped recipe, with
 * {@code air} for an empty square, or any number of entries for a shapeless one.
 *
 * <pre>{@code
 * shapeless = false
 * amount    = 1
 * recipe = [
 *   "air",        "gold_ingot",     "air",
 *   "gold_ingot", "tripwire_hook",  "gold_ingot",
 *   "air",        "gold_ingot",     "air"
 * ]
 * }</pre>
 *
 * <p>A shaped recipe is given a pattern built from the squares that are filled, so a recipe whose whole top
 * row is empty does not demand that the player leave that row empty: the server trims it the way a vanilla
 * recipe is trimmed.
 *
 * <p>Registering the same key twice is not an error. The old one is taken out first, because a reload has to
 * be a thing an operator can do more than once.
 */
public final class Recipes {

    /** What an operator writes for a square that stays empty. */
    private static final String EMPTY = "air";

    /** The letters a shaped pattern is built from, one per distinct ingredient. */
    private static final String LETTERS = "abcdefghi";

    private Recipes() {}

    /**
     * The recipe {@code ingredients} describes, or nothing when it describes none.
     *
     * <p>Empty rather than an exception for an empty list, because "this item is not craftable" is the
     * ordinary case and not a mistake. A list that names something the server does not know is a mistake and
     * throws, with the word in the message: a silent drop would leave an operator staring at a recipe book.
     *
     * @param shapeless whether the squares' positions are ignored
     * @param ingredients nine entries for a shaped recipe, any number for a shapeless one
     * @throws IllegalArgumentException when a word names no material, or a shaped list is not nine long
     */
    public static Optional<Recipe> read(
            NamespacedKey key, ItemStack result, boolean shapeless, List<String> ingredients) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(ingredients, "ingredients");
        List<String> written = ingredients.stream()
                .map(entry -> entry == null ? EMPTY : entry.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (written.stream().allMatch(Recipes::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(shapeless ? shapeless(key, result, written) : shaped(key, result, written));
    }

    /** Put {@code recipe} on the server, taking out whatever held its key before. */
    public static void register(NamespacedKey key, Recipe recipe) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(recipe, "recipe");
        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
    }

    /** Take the recipe at {@code key} off the server, if one is there. */
    public static void unregister(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        Bukkit.removeRecipe(key);
    }

    private static ShapelessRecipe shapeless(NamespacedKey key, ItemStack result, List<String> written) {
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (String entry : written) {
            if (!isEmpty(entry)) {
                recipe.addIngredient(new RecipeChoice.MaterialChoice(material(entry)));
            }
        }
        return recipe;
    }

    private static ShapedRecipe shaped(NamespacedKey key, ItemStack result, List<String> written) {
        if (written.size() != 9) {
            throw new IllegalArgumentException("a shaped recipe is nine entries, got: " + written.size());
        }
        Map<String, Character> letters = new LinkedHashMap<>();
        List<String> rows = new ArrayList<>(3);
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder(3);
            for (int column = 0; column < 3; column++) {
                String entry = written.get(row * 3 + column);
                line.append(isEmpty(entry) ? ' ' : letterFor(letters, entry));
            }
            rows.add(line.toString());
        }
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(rows.toArray(String[]::new));
        for (Map.Entry<String, Character> entry : letters.entrySet()) {
            recipe.setIngredient(entry.getValue(), new RecipeChoice.MaterialChoice(material(entry.getKey())));
        }
        return recipe;
    }

    /** One letter per distinct ingredient, so nine identical ingots do not become nine letters. */
    private static char letterFor(Map<String, Character> letters, String entry) {
        return letters.computeIfAbsent(entry, ignored -> {
            if (letters.size() >= LETTERS.length()) {
                throw new IllegalArgumentException("a recipe holds at most nine different ingredients");
            }
            return LETTERS.charAt(letters.size());
        });
    }

    private static boolean isEmpty(String entry) {
        return entry.isBlank() || EMPTY.equals(entry) || "minecraft:air".equals(entry);
    }

    private static Material material(String entry) {
        Material found = Material.matchMaterial(entry);
        if (found == null) {
            throw new IllegalArgumentException("a recipe names no such material: " + entry);
        }
        return found;
    }
}
