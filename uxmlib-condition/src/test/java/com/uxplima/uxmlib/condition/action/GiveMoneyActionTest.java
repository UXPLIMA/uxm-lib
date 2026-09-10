package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.FakePayingWallet;
import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.condition.Wallet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The grammar could take money and could not pay it.
 *
 * <p>Every plugin that hands a player a wage, a prize or a refund from a content file had the same two
 * roads out of that: name a console command against somebody else's economy plugin, which does nothing on
 * a server that does not run it and says nothing either, or write a verb of its own and teach an operator
 * a second grammar. uxmJobs took the first road, and its four shipped jobs paid their level rewards with
 * {@code [player] uxmjobs points give}: a command that needs the plugin's own admin permission, that
 * breaks the moment an operator renames the root, and that was misspelled against the branch it named, so
 * the rewards paid nothing at all.
 *
 * <p>So the verb is here, beside the one that takes, and it reads the same way: {@code [give-money]
 * <amount>} or {@code [give-money] <currency> <amount>}. It pays through the same {@link
 * com.uxplima.uxmlib.condition.Wallet} the taking verb spends through, so whatever a plugin can charge in
 * it can now pay in.
 *
 * <p>It is not a {@link CostAction}: it costs the player nothing, so the list has nothing to check before
 * it runs. What it does share is the loud failure. A wallet that cannot pay throws rather than returning
 * quietly, because a reward that silently paid nothing is the defect this verb was written to remove.
 */
class GiveMoneyActionTest {

    @Test
    @DisplayName("an amount alone pays the wallet's own default currency")
    void anamountAlonePaysTheDefault() {
        ParsedAction parsed = ActionParser.parse("[give-money] 100");
        assertThat(parsed.type()).isEqualTo(ActionType.GIVE_MONEY);

        FakePayingWallet wallet = new FakePayingWallet("", 250);
        parsed.action().run(context(wallet));

        assertThat(wallet.balance(null, "")).isEqualTo(350);
    }

    @Test
    @DisplayName("a currency and an amount pay that currency")
    void acurrencyAndAnAmount() {
        FakePayingWallet wallet = new FakePayingWallet("coins", 250);

        ActionParser.parse("[give-money] coins 100").action().run(context(wallet));

        assertThat(wallet.balance(null, "coins")).isEqualTo(350);
    }

    @Test
    @DisplayName("a wallet that cannot pay says so rather than paying nothing quietly")
    void awalletThatRefusesThrows() {
        ActionContext refusing = ActionContext.builder(OperandResolver.identity())
                .wallet(new Wallet() {

                    @Override
                    public double balance(@Nullable Player player, String currency) {
                        return 0;
                    }

                    @Override
                    public boolean withdraw(@Nullable Player player, String currency, double amount) {
                        return false;
                    }
                })
                .build();

        assertThatThrownBy(() -> ActionParser.parse("[give-money] 100").action().run(refusing))
                .isInstanceOf(ActionCostException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("a payment costs nothing, so a list holding one checks nothing before it runs")
    void itIsNotACost() {
        assertThat(ActionParser.parse("[give-money] 100").action()).isNotInstanceOf(CostAction.class);
    }

    @Test
    @DisplayName("the amount may be a placeholder, read when the list runs")
    void theamountMayBeAPlaceholder() {
        FakePayingWallet wallet = new FakePayingWallet("", 0);
        ActionContext context = ActionContext.builder((player, template) -> template.replace("%wage%", "42"))
                .wallet(wallet)
                .build();

        ActionList.parse(List.of("[give-money] %wage%")).run(context);

        assertThat(wallet.balance(null, "")).isEqualTo(42);
    }

    @Test
    @DisplayName("a payload that names neither an amount nor a currency and an amount is refused at load")
    void ashapeNobodyCanReadIsRefused() {
        assertThatThrownBy(() -> ActionParser.parse("[give-money] one two three"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("give-money");
    }

    private static ActionContext context(FakePayingWallet wallet) {
        return ActionContext.builder(OperandResolver.identity()).wallet(wallet).build();
    }
}
