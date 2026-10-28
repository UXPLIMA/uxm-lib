package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.wallet.ExperiencePoints;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Experience as money. It needs no economy plugin, so every server has at least this one and a diamond.
 *
 * <p>Two ways to count it, and the operator chooses. Points are the honest one: a player halfway through
 * a level keeps their half. Levels are the one players understand, and taking one from a player halfway
 * through it takes the half as well, which is why the file says so in a comment.
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

    private final CurrencyId id;
    private final Unit unit;
    private final OnlinePlayers players;
    private final Scheduler scheduler;

    public ExperienceCurrency(CurrencyId id, Unit unit, OnlinePlayers players, Scheduler scheduler) {
        this.id = Objects.requireNonNull(id, "id");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
        return new Money(id, BigDecimal.valueOf(onThePlayer(player, this::held).orElse(0)));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        int wanted = Amounts.whole(amount);
        if (wanted < 0) {
            return false;
        }
        return onThePlayer(player, online -> {
                    int has = held(online);
                    if (has < wanted) {
                        return false;
                    }
                    set(online, has - wanted);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean give(UUID player, Money amount) {
        int given = Amounts.whole(amount);
        if (given < 0) {
            return false;
        }
        return onThePlayer(player, online -> {
                    set(online, held(online) + given);
                    return true;
                })
                .orElse(false);
    }

    private <T> Optional<T> onThePlayer(UUID player, java.util.function.Function<Player, T> work) {
        Objects.requireNonNull(player, "player");
        return players.byId(player).flatMap(online -> RegionCall.on(scheduler, online, () -> work.apply(online)));
    }

    private int held(Player player) {
        return unit == Unit.LEVELS ? player.getLevel() : ExperiencePoints.totalOf(player.getLevel(), player.getExp());
    }

    private void set(Player player, int held) {
        if (unit == Unit.LEVELS) {
            player.setLevel(held);
            return;
        }
        ExperiencePoints.Standing standing = ExperiencePoints.standingOf(held);
        player.setLevel(standing.level());
        player.setExp(standing.progress());
    }
}
