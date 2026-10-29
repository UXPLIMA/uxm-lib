package com.uxplima.uxmlib.condition.wallet;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import com.uxplima.uxmlib.condition.action.NameFill;
import org.jspecify.annotations.Nullable;

/**
 * A balance read through a placeholder and taken by a console line.
 *
 * <p>Some economy plugins publish no API at all, and some publish one that changes every release. Nearly
 * all of them publish a placeholder for the balance and a command for a take, because that is what an
 * operator uses every day. This wallet is that pair.
 *
 * <p>A pool may name a second line, which is the one that pays money in. A pool that names none can be read
 * and taken from and not paid into, which is the honest answer for an operator who gave this wallet one
 * command.
 *
 * <p>What it costs is written here, because an operator has to know it. A command answers nothing, so this
 * cannot be told that a take failed. It therefore reads the balance first and refuses a take the player
 * cannot afford, and after that it trusts the line it sent. A plugin that refuses the command quietly is a
 * plugin this wallet cannot use, and {@link BridgedWallet} is there for it.
 *
 * <p>The {@code Exact} methods take a player id and a {@link BigDecimal}, so a plugin can pay a player who is
 * offline and keep every digit of the sum; the ones a condition calls take a live player and a double.
 *
 * <p>A balance that cannot be read as a number is read as zero. Zero is the safe way round: it refuses a
 * take this cannot prove the player can pay, and it never invents money.
 *
 * <p>One instance holds as many pools as it was given, keyed by the currency name a condition writes.
 * The empty name is the default pool, exactly as {@link Wallet} says. A name this wallet was not given
 * reads zero and refuses, which is what an unknown currency does.
 */
public final class PlaceholderWallet implements Wallet {

    /**
     * One balance behind a placeholder, and the line that takes from it.
     *
     * @param placeholder what is asked for the balance, such as {@code %tokens_balance%}
     * @param take the line the console is sent to take money away. {@code {player}}, {@code {uuid}} and
     *     {@code {amount}} are written into it
     * @param thousands what the plugin puts between the groups of three in its answer, removed before the
     *     number is read. An empty one means the answer carries none
     * @param give the line the console is sent to pay money in, written the same way, or {@code null} where
     *     the operator has named none and this pool cannot be paid into
     */
    public record Pool(
            String placeholder,
            String take,
            String thousands,
            @Nullable String give) {

        public Pool {
            Objects.requireNonNull(thousands, "thousands");
            if (placeholder == null || placeholder.isBlank()) {
                throw new IllegalArgumentException("a placeholder pool names no placeholder");
            }
            if (take == null || take.isBlank()) {
                throw new IllegalArgumentException("a placeholder pool names no take command");
            }
            if (give != null && give.isBlank()) {
                throw new IllegalArgumentException("a placeholder pool names a blank give command");
            }
        }

        /**
         * The shape this record had before a pool could be paid into: no give, so a pool written against the
         * older shape reads and takes exactly as it did and refuses every payment.
         */
        public Pool(String placeholder, String take, String thousands) {
            this(placeholder, take, thousands, null);
        }

        /** A pool whose plugin answers a plain number, with nothing between the groups of three. */
        public static Pool of(String placeholder, String take) {
            return new Pool(placeholder, take, "");
        }

        /** This pool, with the line the console is sent to pay money in. */
        public Pool paying(String give) {
            Objects.requireNonNull(give, "give");
            return new Pool(placeholder, take, thousands, give);
        }
    }

    /**
     * What a placeholder answers for one player, online or not. Absent means nobody answered it.
     *
     * <p>It takes an id rather than a player, so a balance can be read for a player who is away.
     */
    @FunctionalInterface
    public interface Reader {
        Optional<String> read(UUID player, String placeholder);
    }

    /** How one line reaches the server. True means the server took the line. */
    @FunctionalInterface
    public interface Console {
        boolean run(String line);
    }

    /** The name the server knows for a player id, absent when it has never seen them. */
    @FunctionalInterface
    public interface Names {
        Optional<String> of(UUID player);

        /** The server's own record of who has played. */
        static Names ofServer() {
            return player -> Optional.ofNullable(Bukkit.getOfflinePlayer(player).getName());
        }
    }

    private final Map<String, Pool> pools;
    private final Reader reader;
    private final Console console;
    private final Names names;

    /**
     * @param names where a name comes from for a line that takes one, when the player is known only by id
     */
    public PlaceholderWallet(Map<String, Pool> pools, Reader reader, Console console, Names names) {
        this.pools = Map.copyOf(Objects.requireNonNull(pools, "pools"));
        this.reader = Objects.requireNonNull(reader, "reader");
        this.console = Objects.requireNonNull(console, "console");
        this.names = Objects.requireNonNull(names, "names");
    }

    /** A wallet that asks the server for the name of a player known only by id. */
    public PlaceholderWallet(Map<String, Pool> pools, Reader reader, Console console) {
        this(pools, reader, console, Names.ofServer());
    }

    /** A wallet over the server's own PlaceholderAPI and console. */
    public static PlaceholderWallet ofServer(Map<String, Pool> pools) {
        return new PlaceholderWallet(pools, new ServerPlaceholders(), ConsoleCommands.ofServer());
    }

    /** A wallet over a single default pool, which is what a plugin with one currency wires. */
    public static PlaceholderWallet ofServer(Pool pool) {
        return ofServer(Map.of("", Objects.requireNonNull(pool, "pool")));
    }

    /** Whether this wallet holds a pool under {@code currency}. */
    public boolean reaches(String currency) {
        Objects.requireNonNull(currency, "currency");
        return pools.containsKey(currency);
    }

    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        return player == null ? 0 : balanceExact(player.getUniqueId(), currency).doubleValue();
    }

    /**
     * The balance as the placeholder answers it, which may be under zero for an economy that allows debt.
     * An unknown pool, or an answer that is not a number, reads zero.
     */
    public BigDecimal balanceExact(UUID player, String currency) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        Pool pool = pools.get(currency);
        if (pool == null) {
            return BigDecimal.ZERO;
        }
        return reader.read(player, pool.placeholder())
                .flatMap(answered -> number(answered, pool.thousands()))
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            return true;
        }
        return player != null
                && take(
                        player.getUniqueId(),
                        currency,
                        BigDecimal.valueOf(amount),
                        () -> Optional.of(player.getName()));
    }

    /** {@link #withdraw} for a player known by id, who may be offline. */
    public boolean withdrawExact(UUID player, String currency, BigDecimal amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        return amount.signum() <= 0 || take(player, currency, amount, () -> names.of(player));
    }

    private boolean take(UUID player, String currency, BigDecimal amount, Supplier<Optional<String>> name) {
        Pool pool = pools.get(currency);
        if (pool == null) {
            return false;
        }
        // The whole cost is read before any of it is taken. A command cannot refuse, so this is the only
        // place the refusal can be made, and it is made before a line is sent.
        if (balanceExact(player, currency).compareTo(amount) < 0) {
            return false;
        }
        return send(pool.take(), player, amount, name);
    }

    /**
     * Pay the amount in, with the line the pool names.
     *
     * <p>Nothing is read first, because there is nothing to refuse: a pay-out cannot overdraw anybody. What
     * the take's own comment says still holds, though, and it holds harder here: a command answers nothing,
     * so this trusts the line it sent. A pool that names no give refuses instead of sending the take line
     * with the sign turned round, which is a guess this class will not make with somebody else's money.
     */
    @Override
    public boolean deposit(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            return true;
        }
        return player != null
                && pay(player.getUniqueId(), currency, BigDecimal.valueOf(amount), () -> Optional.of(player.getName()));
    }

    /** {@link #deposit} for a player known by id, who may be offline. */
    public boolean depositExact(UUID player, String currency, BigDecimal amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        return amount.signum() <= 0 || pay(player, currency, amount, () -> names.of(player));
    }

    private boolean pay(UUID player, String currency, BigDecimal amount, Supplier<Optional<String>> name) {
        Pool pool = pools.get(currency);
        String give = pool == null ? null : pool.give();
        return give != null && send(give, player, amount, name);
    }

    /**
     * Send one line, with the player and the amount written into it.
     *
     * <p>The amount goes in as plain digits with no grouping and no currency name, because what a number
     * looks like to a player is the plugin's business and what a command wants is a number.
     *
     * <p>A line that names the player is not sent when the server has never seen them, because a command
     * with an empty name where the name goes does something else, nor when the name is not one an account
     * could have: a server told to check names less lets one hold a space, which would add an argument to
     * the command. The take or the pay-out then reads as refused.
     */
    private boolean send(String template, UUID player, BigDecimal amount, Supplier<Optional<String>> name) {
        boolean namesThePlayer = NameFill.TOKENS.stream().anyMatch(template::contains);
        Optional<String> named =
                namesThePlayer ? name.get().flatMap(known -> NameFill.into(template, known)) : Optional.of(template);
        return named.map(line -> line.replace("{uuid}", player.toString())
                        .replace("{amount}", amount.stripTrailingZeros().toPlainString()))
                .map(console::run)
                .orElse(false);
    }

    /** The number in the text a placeholder answered with, absent when there is none. */
    private static Optional<BigDecimal> number(String answered, String thousands) {
        String bare = answered.strip();
        if (!thousands.isEmpty()) {
            bare = bare.replace(thousands, "");
        }
        try {
            return Optional.of(new BigDecimal(bare));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
