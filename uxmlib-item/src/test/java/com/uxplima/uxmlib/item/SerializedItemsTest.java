package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The token codec, which is asked rather than trusted.
 *
 * <p>{@code decode} answers with an {@link java.util.Optional}, and an Optional is a promise that the
 * answer may be nothing. It was not kept: a token that is not valid Base64, or valid Base64 that is not
 * an item, threw out of the call. A caller that had written {@code decode(token).ifPresent(...)},
 * which is the shape the signature invites, met an exception instead of an empty.
 *
 * <p>That matters most where the token comes from: a stored click-action payload, written by an earlier
 * version, edited by an operator, or truncated by a database column that was too narrow. Every one of
 * those is a reason to skip the item, and none of them is a reason to throw out of a click.
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
    void aTokenWithoutThePrefixIsNotOurs() {
        assertThat(SerializedItems.decode("DIAMOND_SWORD")).isEmpty();
        assertThat(SerializedItems.isSerialized("DIAMOND_SWORD")).isFalse();
        assertThat(SerializedItems.isSerialized(null)).isFalse();
    }

    @Test
    @DisplayName("a token that is not valid Base64 answers with nothing rather than throwing")
    void amalformedTokenIsEmpty() {
        assertThatCode(() -> SerializedItems.decode("b64:@@@not base64@@@"))
                .describedAs("decode returns an Optional, which promises that nothing is a possible answer")
                .doesNotThrowAnyException();

        assertThat(SerializedItems.decode("b64:@@@not base64@@@")).isEmpty();
    }

    @Test
    @DisplayName("valid Base64 that is not an item answers with nothing rather than throwing")
    void avalidBase64ThatIsNotAnItemIsEmpty() {
        String notAnItem = "b64:"
                + java.util.Base64.getEncoder()
                        .encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatCode(() -> SerializedItems.decode(notAnItem)).doesNotThrowAnyException();
        assertThat(SerializedItems.decode(notAnItem)).isEmpty();
    }

    @Test
    @DisplayName("an empty payload after the prefix answers with nothing")
    void anemptyPayloadIsEmpty() {
        assertThat(SerializedItems.decode("b64:")).isEmpty();
    }
}
