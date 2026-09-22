package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The token codec, and the three answers it gives.
 *
 * <p>The {@link java.util.Optional} is not "maybe something went wrong". It is <b>"maybe this token is
 * not mine"</b>, and that is the distinction the whole codec turns on: a caller walking a chain of icon
 * providers has to tell a material name it should pass along from a token of ours that is damaged.
 * Empty says pass it along. The exception says this one was ours and it is broken.
 *
 * <p>Collapsing the two would make a damaged token indistinguishable from a material name, so a menu
 * would quietly draw the next provider's guess in place of the icon an operator asked for.
 * {@code SerializedStackIconProviderTest} states the same rule from the caller's side and is where it
 * was written down first. This test states it from the codec's, so that the next reader of the
 * signature finds the reason before changing it.
 */
class SerializedItemsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void encodesAndDecodesAnItemBack() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);

        String token = SerializedItems.encode(item);

        assertThat(SerializedItems.isSerialized(token)).isTrue();
        assertThat(SerializedItems.decode(token)).contains(item);
    }

    @Test
    @DisplayName("a token without the prefix is not ours, which is what empty means")
    void atokenWithoutThePrefixIsNotOurs() {
        assertThat(SerializedItems.decode("DIAMOND_SWORD")).isEmpty();
        assertThat(SerializedItems.isSerialized("DIAMOND_SWORD")).isFalse();
        assertThat(SerializedItems.isSerialized(null)).isFalse();
    }

    @Test
    @DisplayName("a token that is ours and damaged throws, so a caller can tell it from one that is not ours")
    void adamagedTokenThrows() {
        assertThatIllegalArgumentException()
                .describedAs("empty means not mine, and a caller walking a provider chain would pass it along")
                .isThrownBy(() -> SerializedItems.decode("b64:@@@not base64@@@"));
    }

    @Test
    @DisplayName("valid Base64 that is not an item is damaged too")
    void avalidBase64ThatIsNotAnItemThrows() {
        String notAnItem = "b64:"
                + java.util.Base64.getEncoder()
                        .encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatIllegalArgumentException().isThrownBy(() -> SerializedItems.decode(notAnItem));
    }
}
