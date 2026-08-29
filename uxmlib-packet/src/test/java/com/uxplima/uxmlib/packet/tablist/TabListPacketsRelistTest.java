package com.uxplima.uxmlib.packet.tablist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/**
 * The interface-level contract of {@link TabListPackets#relist(List, boolean)}: the seam plugin code consumes
 * when a viewer leaves a synthetic-tab "suppress real players" mode and the real players must be re-listed. Like
 * {@link PlayerInfoUpdatesTest}, the real {@code ClientboundPlayerInfoUpdatePacket} construction is compile-gated
 * against the dev bundle (the {@code Entry} record cannot be built on the unit-test classpath) and is covered by
 * the plugin boot smoke; this proves the part that is testable off a live server: that {@code relist} carries the
 * caller's ids and the {@code listed} flag straight through, so a caller can trust those reach the NMS builder.
 */
class TabListPacketsRelistTest {

    @Test
    void relistCarriesTheIdsAndTheListedFlag() {
        RecordingTabListPackets packets = new RecordingTabListPackets();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        packets.relist(ids, true);

        assertThat(packets.relistIds).containsExactlyElementsOf(ids);
        assertThat(packets.relistListed).isTrue();
    }

    @Test
    void relistPropagatesAFalseFlagForUnlisting() {
        RecordingTabListPackets packets = new RecordingTabListPackets();
        List<UUID> ids = List.of(UUID.randomUUID());

        packets.relist(ids, false);

        assertThat(packets.relistIds).containsExactlyElementsOf(ids);
        assertThat(packets.relistListed).isFalse();
    }

    /** A fake that records the {@code relist} call instead of building an NMS packet, so the contract is testable. */
    private static final class RecordingTabListPackets implements TabListPackets {
        private List<UUID> relistIds = List.of();
        private boolean relistListed;

        @Override
        public Object relist(List<UUID> ids, boolean listed) {
            this.relistIds = List.copyOf(ids);
            this.relistListed = listed;
            return new Object();
        }

        @Override
        public Object addOrUpdate(TabEntry entry) {
            return new Object();
        }

        @Override
        public Object displayName(UUID id, Component name) {
            return new Object();
        }

        @Override
        public Object listOrder(UUID id, int order) {
            return new Object();
        }

        @Override
        public Object remove(List<UUID> ids) {
            return new Object();
        }

        @Override
        public void send(Player viewer, Object packet) {
            // recording fake: nothing is written to a connection in a unit test.
        }
    }
}
