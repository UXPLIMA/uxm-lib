package com.uxplima.uxmlib.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import org.geysermc.api.Geyser;
import org.geysermc.api.GeyserApiBase;
import org.geysermc.api.connection.Connection;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.api.util.InputMode;
import org.geysermc.api.util.UiProfile;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A server that runs Geyser without Floodgate sends its Bedrock players forms and knows their Xbox ids. */
class GeyserWithoutFloodgateTest {

    private static final UUID MIRA = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
    private static final UUID ALEX = UUID.fromString("5b1e2f3a-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
    private static final String MIRA_XUID = "2535412345678901";

    /** Geyser keeps one registered API per JVM, so every test here shares this one and resets it. */
    private static final FakeGeyser GEYSER = registered();

    private final Player mira = player(MIRA, "Mira");
    private final Player alex = player(ALEX, "Alex");

    @BeforeEach
    void reset() {
        GEYSER.connections.clear();
        GEYSER.sent.clear();
        GEYSER.connections.put(MIRA, new FakeConnection(MIRA_XUID));
    }

    @Test
    void aBedrockPlayerGetsTheFormThroughGeyserAndTheirTapComesBack() throws Exception {
        BedrockScreen screen = BedrockScreen.forServer(server("Geyser-Spigot"));
        List<Integer> taps = new ArrayList<>();
        List<String> closes = new ArrayList<>();

        assertThat(screen.available()).isTrue();
        screen.sendSimpleForm(
                mira,
                "Shop",
                "Pick a category",
                List.of(new BedrockButton("Tools", null), new BedrockButton("Food", null)),
                taps::add,
                () -> closes.add("closed"));

        assertThat(GEYSER.sent).hasSize(1);
        Sent sent = GEYSER.sent.getFirst();
        assertThat(sent.player()).isEqualTo(MIRA);
        assertThat(sent.form()).isInstanceOfSatisfying(SimpleForm.class, form -> {
            assertThat(form.title()).isEqualTo("Shop");
            assertThat(form.content()).isEqualTo("Pick a category");
            assertThat(form.buttons()).extracting(button -> button.text()).containsExactly("Tools", "Food");
        });

        answer(sent.form(), "1");
        assertThat(taps).containsExactly(1);
        assertThat(closes).isEmpty();
    }

    @Test
    void aDismissedFormRunsItsCloseAndATypedLineComesBack() throws Exception {
        BedrockScreen screen = BedrockScreen.forServer(server("Geyser-Spigot"));
        List<String> closes = new ArrayList<>();
        List<String> typed = new ArrayList<>();

        screen.sendSimpleForm(
                mira, "Shop", null, List.of(new BedrockButton("Tools", null)), index -> {}, () -> closes.add("closed"));
        answer(GEYSER.sent.getFirst().form(), "null");
        assertThat(closes).containsExactly("closed");

        screen.sendInputForm(mira, "Rename", "New name", "Mira", typed::add, () -> closes.add("input closed"));
        answer(GEYSER.sent.get(1).form(), "[\"Mira's pet\"]");
        assertThat(typed).containsExactly("Mira's pet");
        assertThat(closes).containsExactly("closed");
    }

    @Test
    void aFormForAPlayerGeyserDoesNotKnowIsDroppedWithoutFailingTheOpen() {
        BedrockScreen screen = BedrockScreen.forServer(server("Geyser-Spigot"));
        List<Integer> taps = new ArrayList<>();

        screen.sendSimpleForm(alex, "Shop", null, List.of(new BedrockButton("Tools", null)), taps::add);

        assertThat(GEYSER.sent).isEmpty();
        assertThat(taps).isEmpty();
    }

    @Test
    void theDetectorNamesTheBedrockPlayerTheirXuidAndTheirClient() {
        BedrockDetector detector = BedrockDetector.forServer(server("Geyser-Spigot"));

        assertThat(detector.backend()).isEqualTo("geyser");
        assertThat(detector.isBedrock(MIRA)).isTrue();
        assertThat(detector.xuid(MIRA)).contains(MIRA_XUID);
        assertThat(detector.client(MIRA)).hasValueSatisfying(client -> {
            assertThat(client.device()).isEqualTo(BedrockClient.Device.ANDROID);
            assertThat(client.input()).isEqualTo(BedrockClient.Input.TOUCH);
            assertThat(client.layout()).isEqualTo(BedrockClient.Layout.POCKET);
            assertThat(client.version()).isEqualTo("1.21.90");
            assertThat(client.language()).isEqualTo("tr_tr");
        });
        // Without Floodgate a Bedrock player signs in with a Java account, so their name carries no prefix.
        assertThat(detector.namePrefix()).isEmpty();
    }

    @Test
    void aJavaPlayerHasNoXuidAndNoClient() {
        BedrockDetector detector = BedrockDetector.forServer(server("Geyser-Spigot"));

        assertThat(detector.isBedrock(ALEX)).isFalse();
        assertThat(detector.xuid(ALEX)).isEmpty();
        assertThat(detector.client(ALEX)).isEmpty();
    }

    @Test
    void floodgateAnswersWhenBothAreInstalled() {
        Server both = server("floodgate", "Geyser-Spigot");

        assertThat(BedrockDetector.forServer(both).backend()).isEqualTo("floodgate");
        // The Floodgate API is not running in a test, so its send fails and is logged; Geyser is never asked.
        BedrockScreen.forServer(both)
                .sendSimpleForm(mira, "Shop", null, List.of(new BedrockButton("Tools", null)), index -> {});
        assertThat(GEYSER.sent).isEmpty();
    }

    @Test
    void aServerWithNeitherHasNoScreen() {
        Server neither = server();

        assertThat(BedrockScreen.forServer(neither)).isSameAs(BedrockScreen.NONE);
        assertThat(BedrockDetector.forServer(neither)).isSameAs(BedrockDetector.NONE);
    }

    /** Hands {@code form} the raw answer a Bedrock client sends back, the way Geyser does on receipt. */
    private static void answer(Form form, String raw) throws Exception {
        FormDefinitions.instance().definitionFor(form).handleFormResponse(form, raw);
    }

    private static FakeGeyser registered() {
        FakeGeyser geyser = new FakeGeyser();
        Geyser.set(geyser);
        return geyser;
    }

    private static Server server(String... enabled) {
        Set<String> plugins = Set.of(enabled);
        PluginManager manager = (PluginManager) Proxy.newProxyInstance(
                GeyserWithoutFloodgateTest.class.getClassLoader(),
                new Class<?>[] {PluginManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPluginEnabled" -> args[0] instanceof String name && plugins.contains(name);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return (Server) Proxy.newProxyInstance(
                GeyserWithoutFloodgateTest.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> manager;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static Player player(UUID id, String name) {
        return (Player) Proxy.newProxyInstance(
                GeyserWithoutFloodgateTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private record Sent(UUID player, Form form) {}

    /** Geyser as a backend sees it: the connections it holds and the forms it was asked to send. */
    private static final class FakeGeyser implements GeyserApiBase {

        private final Map<UUID, Connection> connections = new HashMap<>();
        private final List<Sent> sent = new ArrayList<>();

        @Override
        public @Nullable Connection connectionByUuid(UUID uuid) {
            return connections.get(uuid);
        }

        @Override
        public @Nullable Connection connectionByXuid(String xuid) {
            return connections.values().stream()
                    .filter(connection -> connection.xuid().equals(xuid))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public boolean isBedrockPlayer(UUID uuid) {
            return connections.containsKey(uuid);
        }

        @Override
        public boolean sendForm(UUID uuid, Form form) {
            if (!connections.containsKey(uuid)) {
                return false;
            }
            sent.add(new Sent(uuid, form));
            return true;
        }

        @Override
        public boolean sendForm(UUID uuid, FormBuilder<?, ?, ?> builder) {
            return sendForm(uuid, builder.build());
        }

        @Override
        public boolean transfer(UUID uuid, String address, int port) {
            return false;
        }

        @Override
        public List<? extends Connection> onlineConnections() {
            return List.copyOf(connections.values());
        }

        @Override
        public int onlineConnectionsCount() {
            return connections.size();
        }

        @Override
        public String usernamePrefix() {
            return "";
        }
    }

    /** A Bedrock phone connection, as Geyser reports it. */
    private record FakeConnection(String xuid) implements Connection {

        @Override
        public String bedrockUsername() {
            return "Mira";
        }

        @Override
        public String javaUsername() {
            return "Mira";
        }

        @Override
        public UUID javaUuid() {
            return MIRA;
        }

        @Override
        public String version() {
            return "1.21.90";
        }

        @Override
        public BedrockPlatform platform() {
            return BedrockPlatform.GOOGLE;
        }

        @Override
        public String languageCode() {
            return "tr_TR";
        }

        @Override
        public UiProfile uiProfile() {
            return UiProfile.POCKET;
        }

        @Override
        public InputMode inputMode() {
            return InputMode.TOUCH;
        }

        @Override
        public boolean isLinked() {
            return false;
        }

        @Override
        public boolean sendForm(Form form) {
            return GEYSER.sendForm(MIRA, form);
        }

        @Override
        public boolean sendForm(FormBuilder<?, ?, ?> builder) {
            return GEYSER.sendForm(MIRA, builder);
        }

        @Override
        public boolean transfer(String address, int port) {
            return false;
        }
    }
}
