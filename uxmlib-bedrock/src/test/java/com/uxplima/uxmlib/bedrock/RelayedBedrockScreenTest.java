package com.uxplima.uxmlib.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.junit.jupiter.api.Test;

/**
 * A plugin carries a backend's forms to a proxy that runs Geyser and brings the answer back. The answer reaches only
 * the form that asked, for the player it was sent to, and once.
 */
class RelayedBedrockScreenTest {

    private static final UUID MIRA = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
    private static final UUID ALEX = UUID.fromString("5b1e2f3a-4c5d-4e6f-8a9b-0c1d2e3f4a5b");

    /** One request as the plugin's frame carries it: the bytes are what crosses the wire. */
    private static final class Carried {
        private final UUID player;
        private final long requestId;
        private final byte[] bytes;

        Carried(UUID player, long requestId, byte[] bytes) {
            this.player = player;
            this.requestId = requestId;
            this.bytes = bytes.clone();
        }

        UUID player() {
            return player;
        }

        long requestId() {
            return requestId;
        }

        byte[] bytes() {
            return bytes.clone();
        }
    }

    private final List<Carried> carried = new ArrayList<>();

    /** The backend half: the request crosses as bytes, as the plugin's frame would carry it. */
    private final RelayedBedrockScreen screen = new RelayedBedrockScreen((player, requestId, request) -> {
        carried.add(new Carried(player, requestId, BedrockFormCodec.encode(request)));
        return true;
    });

    private final Player mira = player(MIRA, "Mira");
    private final Player alex = player(ALEX, "Alex");

    private final List<String> heard = new ArrayList<>();

    @Test
    void aTapOnTheProxyRunsTheBackendsCallback() throws Exception {
        screen.sendSimpleForm(
                mira,
                "Shop",
                "Pick",
                List.of(new BedrockButton("Tools", null), new BedrockButton("Food", null)),
                index -> heard.add("tapped " + index),
                () -> heard.add("closed"));

        Carried request = carried.getFirst();
        assertThat(request.player()).isEqualTo(MIRA);
        assertThat(BedrockFormCodec.decodeRequest(request.bytes())).isInstanceOf(BedrockFormRequest.Simple.class);

        proxyAnswers(request, "1");

        assertThat(heard).containsExactly("tapped 1");
    }

    @Test
    void aTypedSecretReachesOnlyTheFormThatAskedAndOnlyOnce() throws Exception {
        screen.sendInputForm(mira, "PIN", "Your PIN", null, pin -> heard.add("pin " + pin), () -> heard.add("closed"));
        Carried request = carried.getFirst();
        BedrockFormResponse answer = proxyAnswer(request, "[\"4821\"]");

        assertThat(screen.receive(ALEX, request.requestId(), answer))
                .as("another player")
                .isFalse();
        assertThat(screen.receive(MIRA, request.requestId() + 1, answer))
                .as("another request")
                .isFalse();
        assertThat(heard).isEmpty();

        assertThat(screen.receive(MIRA, request.requestId(), answer)).isTrue();
        assertThat(screen.receive(MIRA, request.requestId(), answer))
                .as("a repeat")
                .isFalse();
        assertThat(heard).containsExactly("pin 4821");
    }

    @Test
    void aClosedFormRunsItsCloseAndAModalClosesQuietly() throws Exception {
        screen.sendCustomForm(
                mira,
                "Buy",
                null,
                List.of(new BedrockWidget.Toggle("gift", "Gift", false)),
                values -> heard.add("bought"),
                () -> heard.add("custom closed"));
        screen.sendModalForm(mira, "Sure?", null, "Yes", "No", () -> heard.add("yes"), () -> heard.add("no"));

        proxyAnswers(carried.get(0), "null");
        proxyAnswers(carried.get(1), "null");

        assertThat(heard).containsExactly("custom closed");
    }

    @Test
    void aRelayedAnswerCarriesOnlyTheFieldsTheFormAskedFor() {
        List<Map<String, String>> submitted = new ArrayList<>();
        screen.sendCustomForm(
                mira,
                "Buy",
                null,
                List.of(
                        new BedrockWidget.Label("Prices include tax"),
                        new BedrockWidget.Input("note", "Note", "", ""),
                        new BedrockWidget.Toggle("gift", "Gift", false)),
                submitted::add,
                () -> heard.add("closed"));

        screen.receive(
                MIRA,
                carried.getFirst().requestId(),
                new BedrockFormResponse.Submitted(Map.of("gift", "true", "admin", "true")));

        assertThat(submitted).containsExactly(Map.of("note", "", "gift", "true"));
    }

    @Test
    void aPlayerWhoLeftHearsNothingMore() {
        screen.sendSimpleForm(mira, "Shop", null, List.of(new BedrockButton("Tools", null)), index -> heard.add("tap"));
        long requestId = carried.getFirst().requestId();

        screen.forget(MIRA);

        assertThat(screen.receive(MIRA, requestId, new BedrockFormResponse.Tapped(0)))
                .isFalse();
        assertThat(heard).isEmpty();
    }

    @Test
    void aFormTheRelayCouldNotCarryWaitsForNoAnswer() {
        List<Long> ids = new ArrayList<>();
        RelayedBedrockScreen counting = new RelayedBedrockScreen((player, requestId, request) -> {
            ids.add(requestId);
            return false;
        });

        counting.sendSimpleForm(
                alex, "Shop", null, List.of(new BedrockButton("Tools", null)), index -> heard.add("tap"));

        assertThat(counting.receive(ALEX, ids.getFirst(), new BedrockFormResponse.Tapped(0)))
                .isFalse();
        assertThat(heard).isEmpty();
    }

    @Test
    void aPlayerKeepsOnlyTheNewestUnansweredForms() {
        for (int i = 0; i <= RelayedBedrockScreen.MAX_PENDING; i++) {
            int form = i;
            screen.sendSimpleForm(
                    mira, "Shop", null, List.of(new BedrockButton("Tools", null)), index -> heard.add("form " + form));
        }

        assertThat(screen.receive(MIRA, carried.getFirst().requestId(), new BedrockFormResponse.Tapped(0)))
                .as("the oldest was dropped")
                .isFalse();
        assertThat(screen.receive(MIRA, carried.getLast().requestId(), new BedrockFormResponse.Tapped(0)))
                .isTrue();
        assertThat(heard).containsExactly("form " + RelayedBedrockScreen.MAX_PENDING);
    }

    @Test
    void aPluginSuppliedScreenAndDetectorStandInOnlyWhereNeitherPluginRuns() {
        BedrockDetector relayed = uuid -> uuid.equals(MIRA);

        assertThat(BedrockScreen.forServer(server(), screen)).isSameAs(screen);
        assertThat(BedrockDetector.forServer(server(), relayed)).isSameAs(relayed);
        assertThat(BedrockScreen.resolving(server(), screen).available()).isTrue();
        assertThat(BedrockDetector.resolving(server(), relayed).isBedrock(MIRA)).isTrue();

        assertThat(BedrockScreen.forServer(server("floodgate"), screen)).isNotSameAs(screen);
        assertThat(BedrockDetector.forServer(server("Geyser-Spigot"), relayed).backend())
                .isEqualTo("geyser");
        assertThat(BedrockDetector.resolving(server("floodgate"), relayed).backend())
                .isEqualTo("floodgate");
    }

    /** The proxy half: decode, build the Cumulus form, let the client answer, and carry the answer back. */
    private void proxyAnswers(Carried request, String raw) throws Exception {
        screen.receive(request.player(), request.requestId(), proxyAnswer(request, raw));
    }

    private static BedrockFormResponse proxyAnswer(Carried request, String raw) throws Exception {
        List<byte[]> back = new ArrayList<>();
        Form form = CumulusForms.toForm(
                BedrockFormCodec.decodeRequest(request.bytes()),
                response -> back.add(BedrockFormCodec.encode(response)));
        FormDefinitions.instance().definitionFor(form).handleFormResponse(form, raw);
        return BedrockFormCodec.decodeResponse(back.getFirst());
    }

    private static Server server(String... enabled) {
        Set<String> plugins = Set.of(enabled);
        PluginManager manager = (PluginManager) Proxy.newProxyInstance(
                RelayedBedrockScreenTest.class.getClassLoader(),
                new Class<?>[] {PluginManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPluginEnabled" -> args[0] instanceof String name && plugins.contains(name);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return (Server) Proxy.newProxyInstance(
                RelayedBedrockScreenTest.class.getClassLoader(),
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
                RelayedBedrockScreenTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
