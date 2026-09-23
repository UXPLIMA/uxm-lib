package com.uxplima.uxmlib.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The keys of an operator's file that look like a misspelling of a shipped key.
 *
 * <p>A file that wrote {@code cycel { tick = 5 }} loaded without a word: the merge of the shipped defaults found
 * {@code cycle} missing and added it with its shipped value, so the five was lost. A key the shipped file does not
 * have is a suspect only when a shipped key one or two letters away is missing from the operator's file, which is
 * exactly the case where the operator's value is thrown away. A key of their own, a reward or a currency they
 * added, is never that close to a shipped one and is left alone.
 */
@NullMarked
public final class ConfigTypos {

    /** A key this short is too close to everything to call a misspelling. */
    private static final int SHORTEST = 4;

    private static final int FURTHEST = 2;

    private ConfigTypos() {}

    /** One line per suspect, naming the key as written, where it is, and the shipped key it most likely meant. */
    public static List<String> suspects(ConfigurationNode written, ConfigurationNode shipped) {
        Objects.requireNonNull(written, "written");
        Objects.requireNonNull(shipped, "shipped");
        List<String> found = new ArrayList<>();
        walk(written, shipped, found);
        return found;
    }

    private static void walk(ConfigurationNode written, ConfigurationNode shipped, List<String> found) {
        if (!written.isMap() || !shipped.isMap()) {
            return;
        }
        Map<Object, ? extends ConfigurationNode> theirs = written.childrenMap();
        Map<Object, ? extends ConfigurationNode> ours = shipped.childrenMap();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : theirs.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (ours.containsKey(entry.getKey())) {
                walk(entry.getValue(), ours.get(entry.getKey()), found);
                continue;
            }
            for (Object shippedKey : ours.keySet()) {
                String meant = String.valueOf(shippedKey);
                if (!theirs.containsKey(shippedKey) && near(key, meant)) {
                    found.add("'" + key + "' at " + written.path() + " is not a key this file has, and '" + meant
                            + "' was missing, so its shipped value is used. Did you mean '" + meant + "'?");
                    break;
                }
            }
        }
    }

    private static boolean near(String written, String meant) {
        if (written.length() < SHORTEST || meant.length() < SHORTEST) {
            return false;
        }
        return distance(written.toLowerCase(java.util.Locale.ROOT), meant.toLowerCase(java.util.Locale.ROOT))
                <= FURTHEST;
    }

    /** The edit distance, with a swap of two neighbouring letters counted as one edit. */
    private static int distance(String a, String b) {
        int[][] cost = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            cost[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            cost[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int change = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(Math.min(cost[i - 1][j] + 1, cost[i][j - 1] + 1), cost[i - 1][j - 1] + change);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, cost[i - 2][j - 2] + 1);
                }
                cost[i][j] = best;
            }
        }
        return cost[a.length()][b.length()];
    }
}
