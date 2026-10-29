package com.uxplima.uxmlib.condition.currency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmlib.common.Durations;
import com.uxplima.uxmlib.condition.wallet.Economies;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding;
import com.uxplima.uxmlib.config.HoconConfig;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The currencies of {@code currencies.conf}, read into descriptions.
 *
 * <p>Nine kinds are written here. Four of them, {@code vault}, {@code vaultunlocked},
 * {@code playerpoints} and {@code ecobits}, are shipped descriptions of an economy plugin. {@code treasury}
 * is an economy that no description can reach, because it answers a subscriber instead of returning, so it
 * is written out instead. One is an item and one is the experience of the player. The last two are for an
 * economy plugin the plugin does not ship a description of: {@code bridge} is the description itself, which
 * methods on which class, and {@code placeholder} is a balance read through a placeholder and moved by two
 * commands. An operator whose economy plugin is not one of the shipped ones writes one of those two and
 * needs nothing from us.
 *
 * <p>Each currency carries an {@code order}. HOCON does not keep the order a section was written in, so
 * without it the currency a price with no name is written in would change between two reads of one file.
 * The lowest order is the preferred currency and the first tab of a menu. Two equal orders are decided by
 * the id.
 */
public final class CurrencyFile {

    private CurrencyFile() {}

    /**
     * What the file describes, in the order the plugin uses.
     *
     * @param caller the name the plugin introduces itself by where an economy asks who is moving the money.
     *     VaultUnlocked writes it into its own log beside every transaction, so it is the plugin's name: an
     *     operator reading that log has to be able to tell which plugin moved it
     */
    public static List<CurrencySpec> read(HoconConfig config, String caller) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(caller, "caller");
        if (caller.isBlank()) {
            throw new IllegalArgumentException("caller must name the plugin");
        }
        List<CurrencySpec> read = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                config.nodeAt("currencies").childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            if (!entry.getValue().node("enabled").getBoolean(true)) {
                continue;
            }
            read.add(one(id, entry.getValue(), caller));
        }
        if (read.isEmpty()) {
            throw new IllegalArgumentException("currencies.conf names no currency that is turned on");
        }
        read.sort(Comparator.comparingInt(CurrencySpec::order)
                .thenComparing(spec -> spec.id().value()));
        return List.copyOf(read);
    }

    private static CurrencySpec one(String name, ConfigurationNode node, String caller) {
        CurrencyId id = new CurrencyId(name);
        int order = node.node("order").getInt(1000);
        String type = text(node, "type", "").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "item" -> new CurrencySpec.OfItem(id, order, text(node, "material", ""));
            case "experience" -> new CurrencySpec.OfExperience(id, order, unit(id, text(node, "unit", "points")));
            case "vault" -> economy(id, order, node, Economies.vault(), false);
            case "vaultunlocked" -> economy(id, order, node, Economies.vaultUnlocked(caller), false);
            case "playerpoints" -> economy(id, order, node, Economies.playerPoints(), true);
            case "ecobits" -> economy(id, order, node, Economies.ecoBits(), false);
            case "treasury" ->
                new CurrencySpec.OfTreasury(
                        id, order, text(node, "currency-name", ""), Durations.parse(text(node, "wait-for", "10s")));
            case "placeholder" -> placeholder(id, order, node);
            case "bridge" ->
                economy(id, order, node, binding(id, node), node.node("whole").getBoolean(false));
            case "" -> throw new IllegalArgumentException("the currency " + id + " names no type");
            default -> throw new IllegalArgumentException("the currency " + id + " has an unknown type: " + type);
        };
    }

    private static CurrencySpec.OfPlaceholder placeholder(CurrencyId id, int order, ConfigurationNode node) {
        return new CurrencySpec.OfPlaceholder(
                id,
                order,
                required(id, node, "placeholder"),
                required(id, node, "take"),
                required(id, node, "give"),
                text(node, "thousands", ""),
                node.node("pays-offline").getBoolean(true),
                node.node("whole").getBoolean(false));
    }

    private static ExperienceCurrency.Unit unit(CurrencyId id, String written) {
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "levels" -> ExperienceCurrency.Unit.LEVELS;
            case "points" -> ExperienceCurrency.Unit.POINTS;
            default ->
                throw new IllegalArgumentException(
                        "the currency " + id + " must count levels or points, not: " + written);
        };
    }

    /**
     * One economy currency: the library's description of the plugin, and the three facts that are the
     * plugin's own.
     *
     * <p>An economy plugin that holds several currencies needs the name of the one this plugin prices in,
     * and a server that pays a seller who is away needs to say so. Nothing else about a shipped bridge is
     * an operator's choice: the method names are the plugin's.
     *
     * <p>The empty pool is what an economy of one balance answers to, which is why an unwritten
     * {@code currency-name} reads as one rather than as null.
     */
    private static CurrencySpec.OfEconomy economy(
            CurrencyId id, int order, ConfigurationNode node, EconomyBinding binding, boolean whole) {
        return new CurrencySpec.OfEconomy(
                id,
                order,
                binding,
                text(node, "currency-name", ""),
                node.node("pays-offline").getBoolean(true),
                node.node("whole").getBoolean(whole));
    }

    private static EconomyBinding binding(CurrencyId id, ConfigurationNode node) {
        return new EconomyBinding(
                required(id, node, "plugin"),
                required(id, node, "class"),
                access(id, text(node, "access", "service")),
                node.node("accessor").getString(),
                required(id, node, "balance"),
                required(id, node, "take"),
                required(id, node, "give"),
                argument(id, text(node, "argument", "offline-player")),
                answer(id, text(node, "answer", "boolean")),
                pools(node),
                new EconomyBinding.Calls(
                        node.node("take-negates").getBoolean(false),
                        node.node("naming").getString()));
    }

    /**
     * How a currency name reaches the plugin an operator described.
     *
     * <p>A description that names the class the currency objects come from takes them as objects; one that
     * names a currency and no such class passes the name; and one that names neither holds one balance. A
     * shipped description says which of the three it is for itself, because the answer is the plugin's and
     * not the operator's.
     */
    private static EconomyBinding.Pools pools(ConfigurationNode node) {
        @Nullable String holder = node.node("currency-class").getString();
        @Nullable String lookup = node.node("currency-lookup").getString();
        if (holder != null && lookup != null) {
            return EconomyBinding.Pools.byObject(holder, lookup);
        }
        return node.node("currency-name").getString() == null
                ? EconomyBinding.Pools.one()
                : EconomyBinding.Pools.byName();
    }

    private static EconomyBinding.Access access(CurrencyId id, String written) {
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "service" -> EconomyBinding.Access.SERVICE;
            case "static" -> EconomyBinding.Access.STATIC;
            case "plugin" -> EconomyBinding.Access.PLUGIN;
            case "class" -> EconomyBinding.Access.CLASS;
            default ->
                throw new IllegalArgumentException("the currency " + id
                        + " must reach its plugin by service, static, plugin or class, not: " + written);
        };
    }

    private static EconomyBinding.Argument argument(CurrencyId id, String written) {
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "offline-player" -> EconomyBinding.Argument.OFFLINE_PLAYER;
            case "player-id" -> EconomyBinding.Argument.PLAYER_ID;
            case "player-name" -> EconomyBinding.Argument.PLAYER_NAME;
            default ->
                throw new IllegalArgumentException("the currency " + id
                        + " must pass the player as offline-player, player-id or player-name," + " not: " + written);
        };
    }

    private static EconomyBinding.Answer answer(CurrencyId id, String written) {
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "boolean" -> EconomyBinding.Answer.BOOLEAN;
            case "vault-response" -> EconomyBinding.Answer.VAULT_RESPONSE;
            case "nothing" -> EconomyBinding.Answer.NOTHING;
            default ->
                throw new IllegalArgumentException("the currency " + id
                        + " must read its answer as boolean, vault-response or nothing," + " not: " + written);
        };
    }

    private static String required(CurrencyId id, ConfigurationNode node, String key) {
        String written = text(node, key, "");
        if (written.isBlank()) {
            throw new IllegalArgumentException("the currency " + id + " needs " + key);
        }
        return written;
    }

    private static String text(ConfigurationNode node, String key, String fallback) {
        return Objects.requireNonNullElse(node.node(key).getString(fallback), fallback)
                .trim();
    }
}
