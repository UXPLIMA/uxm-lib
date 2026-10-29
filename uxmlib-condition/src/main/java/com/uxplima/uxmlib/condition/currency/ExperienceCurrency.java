package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.wallet.ExperienceWallet;
import com.uxplima.uxmlib.scheduler.OnlinePlayers;
import com.uxplima.uxmlib.scheduler.RegionCall;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Experience as money. It needs no economy plugin, so every server has at least this one and a diamond.
 *
 * <p>Two ways to count it, and the operator chooses. Points are the honest one: a player halfway through
 * a level keeps their half. Levels are the one players understand, and taking one from a player halfway
 * through it takes the half as well, which is why the file says so in a comment.
 *
 * <p>The counting is {@link ExperienceWallet}'s. What this adds is the hop: the money work runs off the
 * server's threads, and an experience bar is read and written on the thread that owns the player.
 *
 * <p>An absent player cannot be paid in experience, so the plugin writes what they earned into their
 * mailbox instead and they take it when they return.
 */
public final class ExperienceCurrency implements Currency {

    /** How the amount is counted. */
    public enum Unit {
        POINTS,
        LEVELS
    }

    /** The wallet holds one pool, under the default name. */
    private static final String POOL = "";

    private final CurrencyId id;
    private final OnlinePlayers players;
    private final Scheduler scheduler;
    private final ExperienceWallet wallet;

    public ExperienceCurrency(CurrencyId id, Unit unit, OnlinePlayers players, Scheduler scheduler) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unit, "unit");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.wallet = new ExperienceWallet(
                Map.of(POOL, unit == Unit.LEVELS ? ExperienceWallet.Unit.LEVELS : ExperienceWallet.Unit.POINTS));
    }

    @Override
    public CurrencyId id() {
        return id;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean paysOffline() {
        return false;
    }

    @Override
    public boolean isWhole() {
        return true;
    }

    @Override
    public Money balanceOf(UUID player) {
        double held =
                onThePlayer(player, online -> wallet.balance(online, POOL)).orElse(0d);
        return new Money(id, BigDecimal.valueOf((long) held));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        int wanted = Amounts.whole(amount);
        return wanted >= 0
                && onThePlayer(player, online -> wallet.withdraw(online, POOL, wanted))
                        .orElse(false);
    }

    @Override
    public boolean give(UUID player, Money amount) {
        int given = Amounts.whole(amount);
        return given >= 0
                && onThePlayer(player, online -> wallet.deposit(online, POOL, given))
                        .orElse(false);
    }

    private <T> Optional<T> onThePlayer(UUID player, Function<Player, T> work) {
        Objects.requireNonNull(player, "player");
        return players.byId(player).flatMap(online -> RegionCall.on(scheduler, online, () -> work.apply(online)));
    }
}
