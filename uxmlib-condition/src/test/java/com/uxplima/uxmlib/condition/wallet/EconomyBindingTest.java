package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Access;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Answer;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Argument;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Calls;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Pools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A description of an economy refuses to be written in a way the reader could not follow. */
class EconomyBindingTest {

    @Test
    @DisplayName("a static access with no accessor names nothing the reader could call")
    void refusesAstaticAccessWithNoAccessor() {
        assertThatThrownBy(() -> new EconomyBinding(
                        "Money",
                        "com.example.Money",
                        Access.STATIC,
                        null,
                        "getBalance",
                        "take",
                        Argument.PLAYER_ID,
                        Answer.BOOLEAN,
                        Pools.one(),
                        Calls.simple()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("static access");
    }

    @Test
    @DisplayName("a currency that is an object needs the class that hands it out")
    void refusesAcurrencyObjectWithNoHolder() {
        assertThatThrownBy(() -> new Pools(Pools.Style.BY_OBJECT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pools(Pools.Style.BY_OBJECT, "com.example.Currencies", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the three shapes of currency are what they say they are")
    void namesTheThreeShapesOfCurrency() {
        assertThat(Pools.one().style()).isEqualTo(Pools.Style.ONE);
        assertThat(Pools.byName().style()).isEqualTo(Pools.Style.BY_NAME);
        assertThat(Pools.byObject("com.example.Currencies", "getByID").lookupMethod())
                .isEqualTo("getByID");
    }

    @Test
    @DisplayName("a plugin that asks who is moving the money is told, and one that does not is not")
    void introducesTheCallerOnlyWhereItIsAsked() {
        assertThat(Calls.simple().introduction()).isEmpty();
        assertThat(new Calls(false, "uxmLib").introduction()).contains("uxmLib");
    }

    @Test
    @DisplayName("VaultUnlocked records who moved the money, so it cannot be left unnamed")
    void refusesAnUnnamedCallerForVaultUnlocked() {
        assertThatThrownBy(() -> Economies.vaultUnlocked(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the four shipped descriptions say which plugin and which methods they read")
    void describesTheFourShippedEconomies() {
        assertThat(Economies.vault().pluginName()).isEqualTo("Vault");
        assertThat(Economies.vault().answer()).isEqualTo(Answer.VAULT_RESPONSE);
        assertThat(Economies.vaultUnlocked("uxmLib").takeMethod()).isEqualTo("withdraw");
        assertThat(Economies.playerPoints().accessorName()).isEqualTo("getInstance.getAPI");
        assertThat(Economies.playerPoints().answer()).isEqualTo(Answer.BOOLEAN);
        // EcoBits cannot refuse an overdraft, which is what makes the wallet read the balance first.
        assertThat(Economies.ecoBits().answer()).isEqualTo(Answer.NOTHING);
        assertThat(Economies.ecoBits().pools().style()).isEqualTo(Pools.Style.BY_OBJECT);
        assertThat(Economies.ecoBits().calls().takeNegates()).isTrue();
    }

    @Test
    @DisplayName("each shipped description names the method that pays money in")
    void namesTheGiveOfEachShippedEconomy() {
        assertThat(Economies.vault().give()).contains("depositPlayer");
        assertThat(Economies.vaultUnlocked("uxmLib").give()).contains("deposit");
        assertThat(Economies.playerPoints().give()).contains("give");
    }

    @Test
    @DisplayName("an economy whose take is a give of a negative number pays through that same method")
    void readsTheGiveOffTheNegatingTake() {
        // EcoBits names no give. adjustBalance is the give, and the take is that method with a sign on it.
        assertThat(Economies.ecoBits().giveMethod()).isNull();
        assertThat(Economies.ecoBits().give()).contains("adjustBalance");
    }

    @Test
    @DisplayName("a description written before there was a give names none, and cannot be paid into")
    void readsTheOlderShapeAsAnEconomyThatCannotBePaid() {
        EconomyBinding older = new EconomyBinding(
                "Money",
                "com.example.Money",
                Access.SERVICE,
                null,
                "getBalance",
                "take",
                Argument.PLAYER_ID,
                Answer.BOOLEAN,
                Pools.one(),
                Calls.simple());

        assertThat(older.giveMethod()).isNull();
        assertThat(older.give()).isEmpty();
        assertThat(older.takeMethod()).isEqualTo("take");
    }
}
