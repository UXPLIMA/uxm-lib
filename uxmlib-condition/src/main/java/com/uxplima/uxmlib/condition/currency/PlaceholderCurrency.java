package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmlib.condition.action.NameFill;

/**
 * A balance that is read through a placeholder and changed by two commands.
 *
 * <p>Some economy plugins publish no API at all, and some publish one that changes every release. Nearly
 * all of them publish a placeholder for the balance and a command for a give and a take, because that is
 * what an operator uses every day. This currency is that pair: it reads the number a placeholder answers
 * with, and it moves money by sending the two lines an operator wrote in the file.
 *
 * <p>What that costs is written here, because an operator has to know it. A command answers nothing, so
 * the plugin cannot be told that a take failed. It therefore reads the balance first and refuses a take
 * that the player cannot afford, and after that it trusts the line it sent. A plugin that refuses the
 * command quietly is a plugin this currency cannot use, and the bridge type is there for it.
 *
 * <p>A balance that cannot be read as a number is read as zero. Zero is the safe way round: it refuses a
 * purchase that the plugin cannot prove the player can pay, and it never invents money.
 */
public final class PlaceholderCurrency implements Currency {

    /** What a placeholder answers for one player. */
    public interface Reader {

        /** Whether the plugin that answers placeholders is on this server. */
        boolean isReady();

        /** The text this placeholder answers with, absent when nobody answered it. */
        Optional<String> read(UUID player, String placeholder);
    }

    /** How one line reaches the server. True means the server took the line. */
    @FunctionalInterface
    public interface Console {
        boolean run(String line);
    }

    private final CurrencyId id;
    private final CurrencySpec.OfPlaceholder spec;
    private final Reader reader;
    private final Console console;
    private final KnownPlayers players;

    public PlaceholderCurrency(CurrencySpec.OfPlaceholder spec, Reader reader, Console console, KnownPlayers players) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.id = spec.id();
        this.reader = Objects.requireNonNull(reader, "reader");
        this.console = Objects.requireNonNull(console, "console");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    @Override
    public boolean isReady() {
        return reader.isReady();
    }

    @Override
    public boolean paysOffline() {
        return spec.paysOffline();
    }

    @Override
    public boolean isWhole() {
        return spec.whole();
    }

    @Override
    public Money balanceOf(UUID player) {
        Objects.requireNonNull(player, "player");
        return new Money(
                id,
                reader.read(player, spec.placeholder()).flatMap(this::number).orElse(BigDecimal.ZERO));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        if (!balanceOf(player).isAtLeast(amount)) {
            return false;
        }
        return send(spec.take(), player, amount);
    }

    @Override
    public boolean give(UUID player, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        return send(spec.give(), player, amount);
    }

    /**
     * Send one line, with the player and the amount written into it.
     *
     * <p>A line that names a player the server has never seen is not sent. The name is what an economy
     * command takes, and a command with an empty name where the name goes is a command that does
     * something else. Nor is a line whose name is not one an account could have: a server told to check names
     * less lets one hold a space, which would add an argument to the command.
     */
    private boolean send(String template, UUID player, Money amount) {
        boolean namesThePlayer = NameFill.TOKENS.stream().anyMatch(template::contains);
        Optional<String> named = namesThePlayer
                ? players.nameOf(player).flatMap(name -> NameFill.into(template, name))
                : Optional.of(template);
        if (named.isEmpty()) {
            return false;
        }
        String line = named.get()
                .replace("{uuid}", player.toString())
                .replace("{amount}", amount.amount().toPlainString());
        return console.run(line);
    }

    /** The number in the text a placeholder answered with, absent when there is none. */
    private Optional<BigDecimal> number(String answered) {
        String bare = answered.trim();
        if (!spec.thousands().isEmpty()) {
            bare = bare.replace(spec.thousands(), "");
        }
        try {
            return Optional.of(new BigDecimal(bare));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
