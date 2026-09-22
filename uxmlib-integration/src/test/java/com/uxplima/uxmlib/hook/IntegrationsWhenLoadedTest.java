package com.uxplima.uxmlib.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Server;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Integrations resolved once the server has loaded, for a plugin that enables before the ones it reads.
 *
 * <p>A plugin that loads at {@code STARTUP} enables before every ordinary plugin, so an integration resolved while
 * it enables never finds Vault, whatever {@code paper-plugin.yml} says: a load order cannot reach across load
 * phases. uxmEssentials is such a plugin, and on 2026-09-22 its Vault economy and permission capabilities were the
 * no-op for the whole run on a server that had Vault. The capability a caller holds starts as the no-op and becomes
 * the real one when the server says it has finished loading, without the caller holding anything new.
 */
final class IntegrationsWhenLoadedTest {

    private static final String LATE_PLUGIN = "LateIntegration";
    private static final System.Logger SILENT = System.getLogger("IntegrationsWhenLoadedTest");

    private ServerMock server;
    private Plugin owner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        owner = MockBukkit.createMockPlugin("Owner");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a plugin that enables later is bound once the server has loaded, behind the same capability")
    void aLatePluginIsBoundWhenTheServerHasLoaded() {
        Integrations integrations = Integrations.resolveWhenLoaded(owner, SILENT, List.of(new Greetings()));
        Greeting held = integrations.capability(Greeting.class);
        assertThat(held.greet("world")).isEqualTo("world");

        MockBukkit.createMockPlugin(LATE_PLUGIN);
        server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        assertThat(held.greet("world")).isEqualTo("hello world");
        assertThat(integrations.capability(Greeting.class)).isSameAs(held);
    }

    @Test
    @DisplayName("a plugin already enabled is bound at once")
    void aPresentPluginIsBoundAtOnce() {
        MockBukkit.createMockPlugin(LATE_PLUGIN);

        Greeting held = Integrations.resolveWhenLoaded(owner, SILENT, List.of(new Greetings()))
                .capability(Greeting.class);

        assertThat(held.greet("world")).isEqualTo("hello world");
    }

    @Test
    @DisplayName("a plugin that never arrives leaves the no-op in place after the load")
    void aPluginThatNeverArrivesStaysTheNoOp() {
        Greeting held = Integrations.resolveWhenLoaded(owner, SILENT, List.of(new Greetings()))
                .capability(Greeting.class);

        server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        assertThat(held.greet("world")).isEqualTo("world");
    }

    @Test
    @DisplayName("a capability that is not an interface is refused, because only an interface can be swapped")
    void aClassCapabilityIsRefused() {
        assertThatThrownBy(() -> Integrations.resolveWhenLoaded(owner, SILENT, List.of(new Words())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A capability that integrates with nothing. */
    public interface Greeting {

        Greeting ABSENT = name -> name;

        String greet(String name);
    }

    private static final class Greetings implements Integration<Greeting> {

        @Override
        public String pluginName() {
            return LATE_PLUGIN;
        }

        @Override
        public Class<Greeting> capability() {
            return Greeting.class;
        }

        @Override
        public Greeting whenAbsent() {
            return Greeting.ABSENT;
        }

        @Override
        public Greeting whenPresent(Server server) {
            return name -> "hello " + name;
        }
    }

    /** A capability written as a class, which a proxy cannot stand in for. */
    private static final class Words implements Integration<StringBuilder> {

        @Override
        public String pluginName() {
            return LATE_PLUGIN;
        }

        @Override
        public Class<StringBuilder> capability() {
            return StringBuilder.class;
        }

        @Override
        public StringBuilder whenAbsent() {
            return new StringBuilder();
        }

        @Override
        public StringBuilder whenPresent(Server server) {
            return new StringBuilder("present");
        }
    }
}
