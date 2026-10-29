package com.uxplima.uxmlib.condition.currency;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmlib.condition.wallet.EconomyBinding;

/**
 * One currency as {@code currencies.conf} describes it, before the server is asked for anything.
 *
 * <p>Reading the file and building the currency are two steps on purpose. The file can then be proved
 * correct without a server: what an operator wrote, what it means, and what is refused. The step that
 * needs a material or a plugin is the second one.
 */
public sealed interface CurrencySpec {

    CurrencyId id();

    /** Where this currency sits in the menu. The lowest is also the one a price with no currency uses. */
    int order();

    /** An item in the inventory, such as a diamond. */
    record OfItem(CurrencyId id, int order, String material) implements CurrencySpec {

        public OfItem {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(material, "material");
            if (material.isBlank()) {
                throw new IllegalArgumentException("the item currency " + id + " names no material");
            }
        }
    }

    /** The experience of the player, counted in levels or in points. */
    record OfExperience(CurrencyId id, int order, ExperienceCurrency.Unit unit) implements CurrencySpec {

        public OfExperience {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(unit, "unit");
        }
    }

    /**
     * A balance a placeholder answers with, changed by two commands.
     *
     * @param placeholder what the plugin asks for the balance, such as {@code %tokens_balance%}
     * @param take the line the plugin sends to take money away
     * @param give the line the plugin sends to give money
     * @param thousands what the plugin puts between the groups of three, removed before the number is
     *     read. An empty one means the answer carries none
     */
    record OfPlaceholder(
            CurrencyId id,
            int order,
            String placeholder,
            String take,
            String give,
            String thousands,
            boolean paysOffline,
            boolean whole)
            implements CurrencySpec {

        public OfPlaceholder {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(thousands, "thousands");
            if (placeholder == null || placeholder.isBlank()) {
                throw new IllegalArgumentException("the placeholder currency " + id + " names no placeholder");
            }
            if (take == null || take.isBlank()) {
                throw new IllegalArgumentException("the placeholder currency " + id + " names no take command");
            }
            if (give == null || give.isBlank()) {
                throw new IllegalArgumentException("the placeholder currency " + id + " names no give command");
            }
        }
    }

    /**
     * The balance Treasury holds.
     *
     * <p>It has a record of its own rather than a description, because Treasury answers a subscriber
     * instead of returning and a balance is two hops away. No line of a file can say that.
     *
     * @param named the id of the currency inside Treasury. An empty one means its primary currency
     * @param waitFor how long one call may take before the plugin gives up on it
     */
    record OfTreasury(CurrencyId id, int order, String named, Duration waitFor) implements CurrencySpec {

        public OfTreasury {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(named, "named");
            Objects.requireNonNull(waitFor, "waitFor");
            if (waitFor.isNegative() || waitFor.isZero()) {
                throw new IllegalArgumentException("the currency " + id + " must wait longer than nothing");
            }
        }
    }

    /**
     * The balance an economy plugin holds, reached through a description of its methods.
     *
     * <p>The description is the library's: talking to ten economy plugins through one reader is a
     * mechanism and uxmLib owns it. The three things beside it are the plugin's own. {@code pool} is the
     * currency an economy of several holds this balance in, and the empty name is what an economy of one
     * answers to. {@code paysOffline} says whether a seller who is asleep can be paid at all, and
     * {@code isWhole} whether a price of half a unit is a price this currency can carry.
     */
    record OfEconomy(
            CurrencyId id, int order, EconomyBinding binding, String pool, boolean paysOffline, boolean isWhole)
            implements CurrencySpec {

        public OfEconomy {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(pool, "pool");
            // A name written against an economy that holds one balance names nothing in it, and Vault's
            // second argument is a world rather than a currency: a plugin that passed the name would read,
            // and take from, that player's balance in a world of that name. The name is dropped here
            // rather than turning the currency off, because an operator who wrote one on a server that
            // worked yesterday should keep a working currency and a line in the console.
            pool = binding.pools().style() == EconomyBinding.Pools.Style.ONE ? "" : pool;
        }
    }
}
