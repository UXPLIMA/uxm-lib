package com.uxplima.uxmlib.hud.nametag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmlib.hud.FakeScheduler;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The registry itself, with the display faked out: two plugins' contributions end up on one name, a plugin
 * that disables takes only its own part away, a quit drops the name, and a colour that two plugins wanted is
 * reported once rather than silently settled.
 */
class NametagRegistryTest {

    private ServerMock server;
    private FakeNametagSink sink;
    private CapturingLog log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sink = new FakeNametagSink();
        log = new CapturingLog();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NametagRegistry registry() {
        return new NametagRegistry(sink, log.logger());
    }

    @Test
    void twoPluginsShareTheOneNameInsteadOfOverwritingEachOther() {
        PlayerMock player = server.addPlayer();
        NametagRegistry registry = registry();

        registry.contribute(player, NametagContribution.prefix("clans", 100, Component.text("[Wolves]")));
        registry.contribute(player, NametagContribution.color("glow", 100, NamedTextColor.RED));

        ComposedNametag name = sink.shown(player.getUniqueId());
        assertThat(name).isNotNull();
        assertThat(Text.plain(java.util.Objects.requireNonNull(name).prefix())).isEqualTo("[Wolves]");
        assertThat(java.util.Objects.requireNonNull(name).color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    void asecondContributionFromTheSamePluginReplacesItsFirst() {
        PlayerMock player = server.addPlayer();
        NametagRegistry registry = registry();

        registry.contribute(player, NametagContribution.prefix("tags", 100, Component.text("[VIP]")));
        registry.contribute(player, NametagContribution.prefix("tags", 100, Component.text("[MVP]")));

        assertThat(Text.plain(registry.composed(player.getUniqueId()).prefix())).isEqualTo("[MVP]");
    }

    @Test
    void aDisablingPluginTakesBackOnlyItsOwnPart() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        NametagRegistry registry = registry();
        for (PlayerMock player : List.of(first, second)) {
            registry.contribute(player, NametagContribution.prefix("clans", 100, Component.text("[Wolves]")));
            registry.contribute(player, NametagContribution.prefix("tags", 200, Component.text("[VIP]")));
        }

        registry.withdraw("clans");

        assertThat(Text.plain(registry.composed(first.getUniqueId()).prefix())).isEqualTo("[VIP]");
        assertThat(Text.plain(registry.composed(second.getUniqueId()).prefix())).isEqualTo("[VIP]");
    }

    @Test
    void aQuitDropsTheNameTheRegistryWrote() {
        PlayerMock player = server.addPlayer();
        NametagRegistry registry = registry();
        registry.contribute(player, NametagContribution.prefix("tags", 100, Component.text("[VIP]")));

        new NametagListener(registry)
                .onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(sink.cleared()).containsExactly(player.getUniqueId());
        assertThat(sink.shown(player.getUniqueId())).isNull();
    }

    @Test
    void closingHandsTheServerBackEveryNameItWrote() {
        PlayerMock player = server.addPlayer();
        NametagRegistry registry = registry();
        registry.contribute(player, NametagContribution.prefix("tags", 100, Component.text("[VIP]")));

        registry.close();

        assertThat(sink.clearAllCalls()).isEqualTo(1);
        assertThat(sink.shown(player.getUniqueId())).isNull();
    }

    @Test
    void twoPluginsColouringTheSameNameAreBothNamedOnceInTheLog() {
        PlayerMock player = server.addPlayer();
        NametagRegistry registry = registry();

        registry.contribute(player, NametagContribution.color("glow", 50, NamedTextColor.RED));
        registry.contribute(player, NametagContribution.color("tags", 100, NamedTextColor.AQUA));
        registry.contribute(player, NametagContribution.color("tags", 100, NamedTextColor.AQUA));

        assertThat(log.messages()).hasSize(1);
        assertThat(log.messages().get(0)).contains("glow").contains("tags").contains(player.getName());
    }

    @Test
    void aSchedulerMeansTheDisplayIsWrittenOnItsOwnThread() {
        PlayerMock player = server.addPlayer();
        FakeScheduler scheduler = new FakeScheduler();
        NametagRegistry registry = new NametagRegistry(sink, log.logger(), " ", scheduler);

        registry.contribute(player, NametagContribution.prefix("tags", 100, Component.text("[VIP]")));

        assertThat(sink.shown(player.getUniqueId())).isNull(); // nothing written on the calling thread
        assertThat(scheduler.pendingGlobals()).isEqualTo(1);
        scheduler.runGlobals();
        assertThat(sink.shown(player.getUniqueId())).isNotNull();
    }

    /** A logger whose records this test can read back. */
    private static final class CapturingLog {
        private final List<String> messages = new ArrayList<>();
        private final Logger logger = Logger.getAnonymousLogger();

        CapturingLog() {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    messages.add(record.getMessage());
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            });
        }

        Logger logger() {
            return logger;
        }

        List<String> messages() {
            return messages;
        }
    }
}
