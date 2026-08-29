package com.uxplima.uxmlib.hook.economy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault2.economy.Economy;

/**
 * A present-guarded view of the VaultUnlocked economy: the {@code net.milkbowl.vault2.economy} provider, with
 * a {@code BigDecimal}, UUID-keyed, multi-currency API that classic Vault never had. {@link #find()} looks the
 * service up and returns empty when that API or a provider for it is absent, so {@link EconomyBridge#find()}
 * can try it after classic Vault and fall through cleanly. The {@code vault2} classes are touched only past
 * the guard, so a server without them still loads.
 *
 * <p>The guard asks for the class, not for a plugin named {@code VaultUnlocked}. No server ever runs one:
 * VaultUnlocked is a drop-in replacement, so it declares itself as {@code Vault}. Asking for the name found
 * nothing on every server, which left this whole path dead. The check stays private here, beside the type it
 * guards, rather than becoming library surface for a single caller.
 *
 * <p>Every vault2 call is keyed by a requesting plugin name; the library passes a stable {@code "uxmlib"}
 * identifier (economy plugins use it only for logging / per-plugin scoping). Amounts cross the {@code double}
 * bridge surface as {@link BigDecimal} against the provider's default currency.
 */
public final class VaultUnlockedEconomy {

    private static final String PLUGIN_NAME = "uxmlib";

    /** The type this hook needs, named as text so the guard can ask for it without loading it. */
    private static final String VAULT2_ECONOMY = "net.milkbowl.vault2.economy.Economy";

    /** Whether the vault2 API is on this plugin's class path: the condition the JVM applies one line later. */
    private static boolean apiPresent() {
        try {
            Class.forName(VAULT2_ECONOMY, false, VaultUnlockedEconomy.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    private final Economy economy;

    private VaultUnlockedEconomy(Economy economy) {
        this.economy = economy;
    }

    /** The registered vault2 economy, or empty when the vault2 API or a provider for it is absent. */
    public static Optional<VaultUnlockedEconomy> find() {
        if (!apiPresent()) {
            return Optional.empty();
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return Optional.empty();
        }
        // A registration always carries a non-null provider; the unannotated vault2 API hides that from NullAway.
        Economy provider = Objects.requireNonNull(registration.getProvider(), "provider");
        return Optional.of(new VaultUnlockedEconomy(provider));
    }

    /** Wrap an already-resolved {@code economy}: the seam {@link #find()} uses, exposed for tests. */
    static VaultUnlockedEconomy of(Economy economy) {
        return new VaultUnlockedEconomy(Objects.requireNonNull(economy, "economy"));
    }

    /** A player's balance in the default currency. */
    @SuppressWarnings("deprecation") // the single-currency convenience accessor is the right default-currency surface
    public double balance(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        return Objects.requireNonNullElse(economy.getBalance(PLUGIN_NAME, player.getUniqueId()), BigDecimal.ZERO)
                .doubleValue();
    }

    /** Whether a player has at least {@code amount} (VaultUnlocked has no direct check, so the balance is read). */
    public boolean has(OfflinePlayer player, double amount) {
        return balance(player) >= amount;
    }

    /** Withdraw {@code amount} from a player; returns whether the transaction succeeded. */
    public boolean withdraw(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return economy.withdraw(PLUGIN_NAME, player.getUniqueId(), BigDecimal.valueOf(amount))
                .transactionSuccess();
    }

    /** Deposit {@code amount} to a player; returns whether the transaction succeeded. */
    public boolean deposit(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return economy.deposit(PLUGIN_NAME, player.getUniqueId(), BigDecimal.valueOf(amount))
                .transactionSuccess();
    }

    /** The provider's own rendering of {@code amount}. */
    @SuppressWarnings("deprecation") // the default-currency format convenience method is exactly this bridge's need
    public String format(double amount) {
        return Objects.requireNonNullElse(economy.format(BigDecimal.valueOf(amount)), "");
    }

    /** The default currency's singular name. */
    public String currencyNameSingular() {
        return Objects.requireNonNullElse(economy.defaultCurrencyNameSingular(PLUGIN_NAME), "");
    }

    /** The default currency's plural name. */
    public String currencyNamePlural() {
        return Objects.requireNonNullElse(economy.defaultCurrencyNamePlural(PLUGIN_NAME), "");
    }

    /** Adapt this view to an {@link EconomyBridge}. */
    Optional<EconomyBridge> toBridge() {
        return Optional.of(new VaultUnlockedEconomyBridge(this));
    }
}
