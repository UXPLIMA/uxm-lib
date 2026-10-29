package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmlib.scheduler.OnlinePlayers;
import com.uxplima.uxmlib.scheduler.RegionCall;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * An item as money: a diamond, an emerald, or whatever the operator names.
 *
 * <p>The balance is what the player is carrying, so it needs no storage of its own and no economy
 * plugin. A payment that does not fit in the inventory falls at the player's feet rather than
 * disappearing, because money the plugin cannot hand over is money it has taken.
 */
public final class ItemCurrency implements Currency {

    private final CurrencyId id;
    private final Material material;
    private final OnlinePlayers players;
    private final Scheduler scheduler;

    public ItemCurrency(CurrencyId id, Material material, OnlinePlayers players, Scheduler scheduler) {
        this.id = Objects.requireNonNull(id, "id");
        this.material = Objects.requireNonNull(material, "material");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (material.isAir()) {
            throw new IllegalArgumentException("air is not a currency");
        }
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
        return new Money(
                id, BigDecimal.valueOf(onThePlayer(player, this::carried).orElse(0)));
    }

    @Override
    public boolean take(UUID player, Money amount) {
        int wanted = Amounts.whole(amount);
        if (wanted < 0) {
            return false;
        }
        return onThePlayer(player, online -> {
                    if (carried(online) < wanted) {
                        return false;
                    }
                    // What removeItem could not find comes back, and a part payment is no payment: what it did take
                    // goes back and the take is refused.
                    Map<Integer, ItemStack> shortfall =
                            online.getInventory().removeItem(new ItemStack(material, wanted));
                    int missing = shortfall.values().stream()
                            .mapToInt(ItemStack::getAmount)
                            .sum();
                    if (missing > 0) {
                        online.getInventory().addItem(new ItemStack(material, wanted - missing));
                        return false;
                    }
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
                    Map<Integer, ItemStack> leftOver =
                            new HashMap<>(online.getInventory().addItem(new ItemStack(material, given)));
                    for (ItemStack rest : leftOver.values()) {
                        online.getWorld().dropItemNaturally(online.getLocation(), rest);
                    }
                    return true;
                })
                .orElse(false);
    }

    private int carried(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack plain = new ItemStack(material);
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            // The plain item and nothing else: a renamed or enchanted stack of the same material is not money, and
            // counting it let it pay for a purchase that removeItem then could not take.
            if (stack != null && stack.isSimilar(plain)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private <T> Optional<T> onThePlayer(UUID player, java.util.function.Function<Player, T> work) {
        Objects.requireNonNull(player, "player");
        return players.byId(player).flatMap(online -> RegionCall.on(scheduler, online, () -> work.apply(online)));
    }
}
