package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** One economy plugin, read through a description of its methods rather than compiled against. */
class BridgedWalletTest {

    private static final System.Logger LOG = System.getLogger(BridgedWalletTest.class.getName());
    private static final String CALLER = "uxmLibTest";

    private ServerMock server;
    private Player ada;

    @BeforeEach
    void startTheServer() {
        server = MockBukkit.mock();
        ada = server.addPlayer("Ada");
    }

    @AfterEach
    void stopTheServer() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the Vault shape is read through its response object")
    void readsTheVaultShape() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.balance(ada, "")).isEqualTo(100);
        assertThat(wallet.withdraw(ada, "", 40)).isTrue();
        assertThat(vault.balance()).isEqualTo(60);
    }

    @Test
    @DisplayName("a take the economy refuses is read as refused, and nothing is taken")
    void readsARefusal() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(10);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.withdraw(ada, "", 40)).isFalse();
        assertThat(vault.balance()).isEqualTo(10);
    }

    @Test
    @DisplayName("an economy of one balance is never handed the name of a second, so an unknown name reads zero")
    void refusesACurrencyAnEconomyOfOneBalanceCannotHold() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        // Vault's two-argument getBalance takes a world. A wallet that passed "coins" into it would read
        // the balance of a world nobody has, so the name is refused before the call.
        assertThat(wallet.balance(ada, "coins")).isZero();
        assertThat(wallet.withdraw(ada, "coins", 1)).isFalse();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("an economy of whole numbers is handed a whole number")
    void handsAWholeNumberToAWholeEconomy() {
        FakeEconomies.PointsShaped points = new FakeEconomies.PointsShaped(50);
        BridgedWallet wallet = wallet(Economies.playerPoints(), points);

        assertThat(wallet.balance(ada, "")).isEqualTo(50);
        assertThat(wallet.withdraw(ada, "", 20)).isTrue();
        assertThat(points.points()).isEqualTo(30);
    }

    @Test
    @DisplayName("an economy of whole numbers cannot be paid a fraction, and nothing is taken trying")
    void refusesAFractionToAWholeEconomy() {
        FakeEconomies.PointsShaped points = new FakeEconomies.PointsShaped(50);
        BridgedWallet wallet = wallet(Economies.playerPoints(), points);

        assertThat(wallet.withdraw(ada, "", 1.5)).isFalse();
        assertThat(points.points()).isEqualTo(50);
    }

    @Test
    @DisplayName("a plugin that is not there reads zero and takes nothing, and never fails")
    void staysQuietWhenThePluginIsAbsent() {
        BridgedWallet wallet = wallet(Economies.vault(), null);

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a plugin that renamed its methods turns its own wallet off")
    void staysOffWhenTheNamesChanged() {
        BridgedWallet wallet = wallet(Economies.vault(), new FakeEconomies.Renamed());

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a call that fails is read as not having happened")
    void readsAFailureAsNoPayment() {
        BridgedWallet wallet = wallet(Economies.vault(), new FakeEconomies.Broken());

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a utility class is called on no object, with a currency object and a decimal")
    void readsAStaticUtilityClass() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("bits", "100.50");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.balance(ada, "bits")).isEqualTo(100.50);
        assertThat(bits.balance()).isEqualByComparingTo("100.50");
    }

    @Test
    @DisplayName("an economy with one adjust method is given a negative number for a take")
    void takesByGivingANegativeNumber() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("negating", "40");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.withdraw(ada, "negating", 15)).isTrue();
        assertThat(bits.balance()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("an economy that cannot refuse is read first, so an overdraft takes nothing at all")
    void readsTheBalanceBeforeAnEconomyThatCannotRefuse() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("short", "10");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.withdraw(ada, "short", 11)).isFalse();
        assertThat(bits.balance()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("a currency the plugin does not hold reads zero and takes nothing")
    void staysOffWhenThePluginHasNoSuchCurrency() {
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.balance(ada, "nothing of that name")).isZero();
        assertThat(wallet.withdraw(ada, "nothing of that name", 1)).isFalse();
    }

    @Test
    @DisplayName("an economy that names its currencies is not asked about the empty name")
    void refusesTheEmptyNameOnAnEconomyOfSeveralBalances() {
        FakeEconomies.Held.of("bits", "50");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("VaultUnlocked is told who is asking, and keeps the last penny of a decimal")
    void readsTheUnlockedShape() {
        FakeEconomies.UnlockedShaped unlocked = new FakeEconomies.UnlockedShaped("100.05");
        BridgedWallet wallet = wallet(Economies.vaultUnlocked(CALLER), unlocked);

        assertThat(wallet.balance(ada, "")).isEqualTo(100.05);
        assertThat(unlocked.lastCaller()).isEqualTo(CALLER);
        assertThat(wallet.withdraw(ada, "", 0.05)).isTrue();
        assertThat(unlocked.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("a take VaultUnlocked refuses is read as refused")
    void readsAnUnlockedRefusal() {
        FakeEconomies.UnlockedShaped unlocked = new FakeEconomies.UnlockedShaped("1");
        BridgedWallet wallet = wallet(Economies.vaultUnlocked(CALLER), unlocked);

        assertThat(wallet.withdraw(ada, "", 40)).isFalse();
        assertThat(unlocked.balance()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("nobody is not a player, so nothing is read and nothing is taken")
    void answersForNoPlayerAtAll() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.balance(null, "")).isZero();
        assertThat(wallet.withdraw(null, "", 1)).isFalse();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("a take of nothing takes nothing and succeeds, as the wallet contract says")
    void takesNothingForANonPositiveAmount() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.withdraw(ada, "", 0)).isTrue();
        assertThat(wallet.withdraw(ada, "", -5)).isTrue();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("the object behind the economy is asked for once and kept")
    void resolvesTheHandleOnceAndKeepsIt() {
        AtomicInteger asked = new AtomicInteger();
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet =
                new BridgedWallet(Economies.vault(), counting(vault, asked), PlayerArguments.ofServer(), LOG);

        wallet.balance(ada, "");
        wallet.balance(ada, "");
        wallet.withdraw(ada, "", 1);

        assertThat(asked).hasValue(1);
    }

    // -- a player who is not here ------------------------------------------------------------------------

    /**
     * The whole reason this wallet works from an id.
     *
     * <p>An auction pays the seller when the listing sells, and the seller is usually asleep. uxmAuction and
     * uxmShop each carried a copy of this class for exactly that, because the wallet took a live
     * {@link Player} and there was none to pass. An economy that keeps a balance in a table does not care
     * whether the owner is connected, so neither does this.
     */
    @Test
    @DisplayName("a balance is read for a player who is not on the server")
    void readsTheBalanceOfAnAbsentPlayer() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);
        UUID absent = UUID.randomUUID();

        assertThat(wallet.balanceOf(absent, "")).isEqualTo(100);
    }

    @Test
    @DisplayName("a player who is not on the server is paid and charged like anybody else")
    void movesTheMoneyOfAnAbsentPlayer() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);
        UUID absent = UUID.randomUUID();

        assertThat(wallet.withdrawFrom(absent, "", 40)).isTrue();
        assertThat(vault.balance()).isEqualTo(60);
        assertThat(wallet.depositTo(absent, "", 15)).isTrue();
        assertThat(vault.balance()).isEqualTo(75);
    }

    /** The overdraft refusal is the same on both roads, because it is the same method underneath. */
    @Test
    @DisplayName("an absent player is refused an overdraft exactly as a present one is")
    void refusesAnAbsentOverdraft() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(10);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.withdrawFrom(UUID.randomUUID(), "", 40)).isFalse();
        assertThat(vault.balance()).isEqualTo(10);
    }

    /** The player road is the id road with the id taken off the player, and this pins that they agree. */
    @Test
    @DisplayName("the player road and the id road are the same road")
    void thePlayerRoadIsTheIdRoad() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.balance(ada, "")).isEqualTo(wallet.balanceOf(ada.getUniqueId(), ""));
    }

    // -- money that must not be rounded ------------------------------------------------------------------

    /**
     * An auction house prices in exact sums and a double is not one.
     *
     * <p>{@link Wallet} answers in doubles because a cost check asks "is there enough", and there a double
     * is honest. A house that takes a bid, holds it and pays it out again cannot round any of the three,
     * which is the second reason uxmAuction and uxmShop each carried their own copy of this class. The
     * exact road hands the economy the number it was given, in the shape that economy's parameter asks
     * for.
     */
    @Test
    @DisplayName("an exact amount reaches an economy that counts in decimals unrounded")
    void keepsAnExactAmount() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.withdrawExact(ada.getUniqueId(), "", new BigDecimal("40.25")))
                .isTrue();
        assertThat(vault.balance()).isEqualTo(59.75);
    }

    @Test
    @DisplayName("the exact balance is the balance, as a number nothing was dropped from")
    void readsAnExactBalance() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(12.5);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.balanceExact(ada.getUniqueId(), "")).isEqualByComparingTo(new BigDecimal("12.5"));
    }

    /**
     * An economy that counts in whole numbers cannot be paid a fraction, and the refusal is made here
     * rather than by rounding it into one that can.
     */
    @Test
    @DisplayName("an economy of whole numbers refuses a fraction rather than rounding it")
    void refusesAFractionAWholeEconomyCannotHold() {
        FakeEconomies.PointsShaped points = new FakeEconomies.PointsShaped(50);
        BridgedWallet wallet = wallet(Economies.playerPoints(), points);

        assertThat(wallet.withdrawExact(ada.getUniqueId(), "", new BigDecimal("0.5")))
                .isFalse();
        assertThat(points.points()).isEqualTo(50);
    }

    // -- whether there is anything behind it -------------------------------------------------------------

    /**
     * A house that offers a currency has to know whether it can pay in it.
     *
     * <p>Reading zero and refusing every take is the right answer for a cost check, which is what
     * {@link Wallet} is for. It is the wrong answer for a house: an auction that lists an item priced in a
     * currency nothing answers takes a seller's item and can never sell it. So the house asks first, and
     * leaves the currency out of the list when the answer is no.
     */
    @Test
    @DisplayName("a wallet says whether the economy behind it can answer at all")
    void saysWhetherItReachesTheEconomy() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);

        assertThat(wallet(Economies.vault(), vault).reaches("")).isTrue();
        assertThat(wallet(Economies.vault(), null).reaches(""))
                .describedAs("the plugin is not on this server")
                .isFalse();
    }

    @Test
    @DisplayName("an economy that is here but has renamed its methods does not reach either")
    void doesNotReachARenamedEconomy() {
        assertThat(wallet(Economies.vault(), new FakeEconomies.Renamed()).reaches(""))
                .isFalse();
    }

    @Test
    @DisplayName("a wallet says which economy it reads, so a caller can log it")
    void namesItsOwnBinding() {
        assertThat(wallet(Economies.vault(), null).binding().pluginName()).isEqualTo("Vault");
    }

    @Test
    @DisplayName("the Vault shape is paid through its own deposit, and the answer is read the same way")
    void paysTheVaultShape() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.deposit(ada, "", 40)).isTrue();
        assertThat(vault.balance()).isEqualTo(140);
    }

    @Test
    @DisplayName("an economy with one adjust method is paid with a positive number, not the take's negative")
    void paysByGivingAPositiveNumber() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("paying", "40");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.deposit(ada, "paying", 15)).isTrue();
        assertThat(bits.balance()).isEqualByComparingTo("55");
    }

    @Test
    @DisplayName("an economy of whole numbers cannot be paid a fraction, and nothing arrives trying")
    void refusesToPayAFractionToAWholeEconomy() {
        FakeEconomies.PointsShaped points = new FakeEconomies.PointsShaped(50);
        BridgedWallet wallet = wallet(Economies.playerPoints(), points);

        assertThat(wallet.deposit(ada, "", 20)).isTrue();
        assertThat(points.points()).isEqualTo(70);
        assertThat(wallet.deposit(ada, "", 1.5)).isFalse();
        assertThat(points.points()).isEqualTo(70);
    }

    @Test
    @DisplayName("VaultUnlocked is told who is paying too")
    void paysTheUnlockedShape() {
        FakeEconomies.UnlockedShaped unlocked = new FakeEconomies.UnlockedShaped("1.00");
        BridgedWallet wallet = wallet(Economies.vaultUnlocked(CALLER), unlocked);

        assertThat(wallet.deposit(ada, "", 0.05)).isTrue();
        assertThat(unlocked.balance()).isEqualByComparingTo("1.05");
        assertThat(unlocked.lastCaller()).isEqualTo(CALLER);
    }

    @Test
    @DisplayName("a description that names no give cannot pay, and still reads and still takes")
    void refusesToPayThroughADescriptionWithNoGive() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(noGive(), vault);

        assertThat(wallet.deposit(ada, "", 40)).isFalse();
        assertThat(wallet.balance(ada, "")).isEqualTo(100);
        assertThat(wallet.withdraw(ada, "", 40)).isTrue();
        assertThat(vault.balance()).isEqualTo(60);
    }

    @Test
    @DisplayName("an economy that lost the give it named keeps its balance and its take, and pays nothing")
    void keepsReadingAndTakingWhenOnlyTheGiveIsGone() {
        FakeEconomies.TakeOnly vault = new FakeEconomies.TakeOnly(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.deposit(ada, "", 40)).isFalse();
        assertThat(wallet.balance(ada, "")).isEqualTo(100);
        assertThat(wallet.withdraw(ada, "", 40)).isTrue();
        assertThat(vault.balance()).isEqualTo(60);
    }

    @Test
    @DisplayName("a plugin that is not there pays nothing, and never fails")
    void paysNothingWhenThePluginIsAbsent() {
        assertThat(wallet(Economies.vault(), null).deposit(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a payment that fails on the way in is read as not having happened")
    void readsAFailedPaymentAsNoPayment() {
        assertThat(wallet(Economies.vault(), new FakeEconomies.Broken()).deposit(ada, "", 1))
                .isFalse();
    }

    @Test
    @DisplayName("nobody is not a player, so nothing is paid")
    void paysNoPlayerAtAll() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.deposit(null, "", 1)).isFalse();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("a payment of nothing pays nothing and succeeds, as the wallet contract says")
    void paysNothingForANonPositiveAmount() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.deposit(ada, "", 0)).isTrue();
        assertThat(wallet.deposit(ada, "", -5)).isTrue();
        assertThat(vault.balance()).isEqualTo(100);
    }

    /** The Vault description as it was written before an economy could be asked to pay out. */
    private static EconomyBinding noGive() {
        return new EconomyBinding(
                "Vault",
                "net.milkbowl.vault.economy.Economy",
                EconomyBinding.Access.SERVICE,
                null,
                "getBalance",
                "withdrawPlayer",
                EconomyBinding.Argument.OFFLINE_PLAYER,
                EconomyBinding.Answer.VAULT_RESPONSE,
                EconomyBinding.Pools.one(),
                EconomyBinding.Calls.simple());
    }

    /** The EcoBits description, pointed at the classes this test owns. */
    private static EconomyBinding ecoBitsShaped() {
        return new EconomyBinding(
                "EcoBits",
                FakeEconomies.UtilityShaped.class.getName(),
                EconomyBinding.Access.CLASS,
                null,
                "getBalance",
                "adjustBalance",
                EconomyBinding.Argument.PLAYER_ID,
                EconomyBinding.Answer.NOTHING,
                EconomyBinding.Pools.byObject(FakeEconomies.Held.class.getName(), "getByID"),
                new EconomyBinding.Calls(true, null));
    }

    private static BridgedWallet wallet(EconomyBinding binding, @Nullable Object provider) {
        return new BridgedWallet(binding, holding(provider), PlayerArguments.ofServer(), LOG);
    }

    /** The seam a wallet reads its object from, holding one object or nothing at all. */
    private static EconomyProviders holding(@Nullable Object provider) {
        return counting(provider, new AtomicInteger());
    }

    /** The same seam, counting how often it was asked. */
    private static EconomyProviders counting(@Nullable Object provider, AtomicInteger asked) {
        return new EconomyProviders() {

            @Override
            public Optional<Object> provider(EconomyBinding binding) {
                asked.incrementAndGet();
                return Optional.ofNullable(provider);
            }

            @Override
            public Optional<Object> service(String pluginName, String className) {
                return Optional.empty();
            }
        };
    }
}
