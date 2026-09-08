package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The wallet contract itself: what the empty wallet answers, and what a wallet that cannot pay out says. */
class WalletTest {

    @Test
    @DisplayName("the empty wallet reads zero, takes nothing and pays nothing")
    void theEmptyWalletAnswersNothingToEverything() {
        Wallet empty = Wallet.empty();

        assertThat(empty.balance(null, "")).isZero();
        assertThat(empty.withdraw(null, "", 5)).isFalse();
        assertThat(empty.deposit(null, "", 5)).isFalse();
    }

    @Test
    @DisplayName("a wallet written before there was a deposit keeps reading and taking, and refuses to pay")
    void aWalletWithNoDepositOfItsOwnRefusesToPay() {
        // FakeWallet implements balance and withdraw and nothing else, which is every wallet a consumer
        // wrote against the older contract. It still compiles, it still reads, it still takes, and it says
        // plainly that it cannot pay out rather than reporting a payment that never happened.
        FakeWallet wallet = new FakeWallet("coins", 100);

        assertThat(wallet.deposit(null, "coins", 25)).isFalse();
        assertThat(wallet.balance(null, "coins")).isEqualTo(100);
        assertThat(wallet.withdraw(null, "coins", 25)).isTrue();
        assertThat(wallet.balance(null, "coins")).isEqualTo(75);
    }
}
