package com.uxplima.uxmlib.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The optional-plugin integration contract, proven on an integration with nothing behind it: resolved once,
 * real when its plugin is present, a no-op when it is absent, one integration per capability, and a real
 * side that throws degrades to the no-op rather than taking the plugin's enable down with it.
 *
 * <p>Ported from uxmEssentials, where this model was written and proven first.
 */
final class IntegrationsTest {

    private static final String FAKE_PLUGIN = "ExampleIntegration";
    private static final System.Logger SILENT = System.getLogger("IntegrationsTest");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an absent plugin resolves to the no-op default, and calling it is safe")
    void anAbsentPluginResolvesToTheNoOp() {
        Greetings integration = new Greetings();

        Greeting greeting =
                Integrations.resolve(server, SILENT, List.of(integration)).capability(Greeting.class);

        assertThat(greeting).isSameAs(Greeting.ABSENT);
        assertThat(greeting.available()).isFalse();
        assertThatCode(() -> assertThat(greeting.greet("world")).isEqualTo("world"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a present plugin resolves to the real side")
    void aPresentPluginResolvesToTheRealSide() {
        MockBukkit.createMockPlugin(FAKE_PLUGIN);

        Greeting greeting =
                Integrations.resolve(server, SILENT, List.of(new Greetings())).capability(Greeting.class);

        assertThat(greeting).isNotSameAs(Greeting.ABSENT);
        assertThat(greeting.available()).isTrue();
        assertThat(greeting.greet("world")).isEqualTo("hello world");
    }

    @Test
    @DisplayName("a capability is looked up by type, and an unknown type is an error rather than a null")
    void aCapabilityIsLookedUpByType() {
        Integrations integrations = Integrations.resolve(server, SILENT, List.of(new Greetings()));

        assertThat(integrations.provides(Greeting.class)).isTrue();
        assertThat(integrations.provides(String.class)).isFalse();
        assertThatThrownBy(() -> integrations.capability(String.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("two integrations claiming one capability is refused")
    void twoIntegrationsForOneCapabilityAreRefused() {
        assertThatThrownBy(() -> Integrations.resolve(server, SILENT, List.of(new Greetings(), new Greetings())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a real side that cannot be built degrades to the no-op")
    void aBrokenRealSideDegradesToTheNoOp() {
        // An incompatible SDK surfaces inside whenPresent as a linkage error, and one broken integration must
        // not take the whole enable with it.
        MockBukkit.createMockPlugin(FAKE_PLUGIN);

        Greeting greeting =
                Integrations.resolve(server, SILENT, List.of(new Broken())).capability(Greeting.class);

        assertThat(greeting).isSameAs(Greeting.ABSENT);
    }

    /** A capability that integrates with nothing: enough surface to tell present, absent and no-op apart. */
    private interface Greeting {

        Greeting ABSENT = new Greeting() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String greet(String name) {
                return name;
            }
        };

        boolean available();

        String greet(String name);
    }

    private static class Greetings implements Integration<Greeting> {

        @Override
        public String pluginName() {
            return FAKE_PLUGIN;
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
            return new Greeting() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public String greet(String name) {
                    return "hello " + name;
                }
            };
        }
    }

    /** Stands in for an incompatible or partial SDK. */
    private static final class Broken extends Greetings {

        @Override
        public Greeting whenPresent(Server server) {
            throw new NoClassDefFoundError("com/example/MissingSdkType");
        }
    }
}
