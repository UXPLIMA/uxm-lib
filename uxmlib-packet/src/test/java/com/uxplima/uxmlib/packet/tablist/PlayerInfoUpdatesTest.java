package com.uxplima.uxmlib.packet.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The pure, NMS-free part of {@link PlayerInfoUpdates}: the {@link PlayerInfoUpdates#affectsListing} guard that
 * decides whether forcing a packet unlisted can change tab visibility. The real entry reconstruction in
 * {@code NmsPlayerInfoUpdates} is compile-gated against the dev bundle (the {@code ClientboundPlayerInfoUpdatePacket.Entry}
 * record cannot be built on the unit-test classpath, like the rest of the packet NMS) and is covered by the plugin
 * boot smoke; this proves the only branch that is testable without a live server: that the action-set guard
 * short-circuits a no-op rewrite so a pure latency/display-name update is forwarded unchanged.
 */
class PlayerInfoUpdatesTest {

    @Test
    void addPlayerActionAffectsListing() {
        assertThat(PlayerInfoUpdates.affectsListing(List.of(PlayerInfoUpdates.ADD_PLAYER)))
                .isTrue();
    }

    @Test
    void updateListedActionAffectsListing() {
        assertThat(PlayerInfoUpdates.affectsListing(List.of(PlayerInfoUpdates.UPDATE_LISTED)))
                .isTrue();
    }

    @Test
    void anActionSetCarryingEitherListingActionAmongOthersAffectsListing() {
        assertThat(PlayerInfoUpdates.affectsListing(
                        List.of("UPDATE_LATENCY", PlayerInfoUpdates.UPDATE_LISTED, "UPDATE_DISPLAY_NAME")))
                .isTrue();
    }

    @Test
    void anActionSetTouchingNeitherListingActionDoesNotAffectListing() {
        // A pure latency or display-name or list-order update cannot change a client's tab visibility, so forcing
        // it unlisted is a no-op and forceUnlisted returns null (the caller forwards the original).
        assertThat(PlayerInfoUpdates.affectsListing(
                        List.of("UPDATE_LATENCY", "UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER")))
                .isFalse();
    }

    @Test
    void anEmptyActionSetDoesNotAffectListing() {
        assertThat(PlayerInfoUpdates.affectsListing(List.of())).isFalse();
    }

    @Test
    void affectsListingRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> PlayerInfoUpdates.affectsListing(nullNames()));
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the entry-point guard fires.
    private static Iterable<String> nullNames() {
        return null;
    }
}
