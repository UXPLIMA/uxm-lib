package com.uxplima.uxmlib.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Servers that talk through a broker on this machine, the way a network's servers talk through its proxy. */
class DirectNetworkTest {

    private static final System.Logger LOG = System.getLogger("test");
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final String lobbySecret = Signer.newSecret();
    private final String survivalSecret = Signer.newSecret();
    private final List<AutoCloseable> open = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeAll() throws Exception {
        for (AutoCloseable closeable : open) {
            closeable.close();
        }
    }

    private DirectBroker broker() throws IOException {
        DirectBroker broker = new DirectBroker(
                "127.0.0.1",
                0,
                Map.of("lobby", lobbySecret, "survival", survivalSecret, "creative", Signer.newSecret()),
                LOG);
        open.add(broker);
        return broker;
    }

    private DirectBus server(DirectBroker broker, String id, String secret) {
        DirectBus bus = new DirectBus(id, "127.0.0.1", broker.port(), secret, LOG);
        open.add(bus);
        return bus;
    }

    private static void await(BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!done.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            Thread.sleep(20);
        }
    }

    private static byte[] text(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aLinePublishedOnOneServerReachesTheOthersAndNotItself() throws Exception {
        DirectBroker broker = broker();
        DirectBus lobby = server(broker, "lobby", lobbySecret);
        DirectBus survival = server(broker, "survival", survivalSecret);
        List<Frame> heardBySurvival = new CopyOnWriteArrayList<>();
        List<Frame> heardByLobby = new CopyOnWriteArrayList<>();
        survival.subscribe("chat", heardBySurvival::add);
        lobby.subscribe("chat", heardByLobby::add);
        await(() -> broker.connected().containsAll(List.of("lobby", "survival")));

        lobby.publish("chat", text("hello"));

        await(() -> !heardBySurvival.isEmpty());
        assertThat(heardBySurvival).singleElement().satisfies(frame -> {
            assertThat(frame.origin()).isEqualTo("lobby");
            assertThat(new String(frame.payload(), StandardCharsets.UTF_8)).isEqualTo("hello");
        });
        Thread.sleep(200);
        assertThat(heardByLobby).isEmpty();
    }

    @Test
    void aFrameSentToOneServerReachesOnlyThatServer() throws Exception {
        DirectBroker broker = broker();
        DirectBus lobby = server(broker, "lobby", lobbySecret);
        DirectBus survival = server(broker, "survival", survivalSecret);
        List<Frame> heardBySurvival = new CopyOnWriteArrayList<>();
        List<Frame> heardByBroker = new CopyOnWriteArrayList<>();
        survival.subscribe("pm", heardBySurvival::add);
        broker.subscribe("pm", heardByBroker::add);
        await(() -> broker.connected().containsAll(List.of("lobby", "survival")));

        lobby.send("survival", "pm", text("psst"));

        await(() -> !heardBySurvival.isEmpty());
        Thread.sleep(200);
        assertThat(heardByBroker).isEmpty();
    }

    @Test
    void aServerWithTheWrongSecretIsNeverLetIn() throws Exception {
        DirectBroker broker = broker();
        DirectBus survival = server(broker, "survival", survivalSecret);
        DirectBus impostor = server(broker, "lobby", Signer.newSecret());
        List<Frame> heard = new CopyOnWriteArrayList<>();
        survival.subscribe("chat", heard::add);
        await(() -> broker.connected().contains("survival"));

        impostor.publish("chat", text("let me in"));

        Thread.sleep(1000);
        assertThat(broker.connected()).doesNotContain("lobby");
        assertThat(heard).isEmpty();
    }

    @Test
    void anIdTheBrokerDoesNotKnowIsNeverLetIn() throws Exception {
        DirectBroker broker = broker();
        DirectBus stranger = server(broker, "stranger", lobbySecret);

        stranger.publish("chat", text("hello"));

        Thread.sleep(1000);
        assertThat(broker.connected()).isEmpty();
    }

    @Test
    void theProxyPublishesLikeAnyServer() throws Exception {
        DirectBroker broker = broker();
        DirectBus lobby = server(broker, "lobby", lobbySecret);
        List<Frame> heard = new CopyOnWriteArrayList<>();
        lobby.subscribe("route", heard::add);
        await(() -> broker.connected().contains("lobby"));

        broker.publish("route", text("joined"));

        await(() -> !heard.isEmpty());
        assertThat(heard.getFirst().origin()).isEqualTo(DirectBroker.PROXY);
    }

    @Test
    void aServerCalledProxyIsNeverLetInSinceThatIdIsTheBrokers() throws Exception {
        String secret = Signer.newSecret();
        DirectBroker broker = new DirectBroker("127.0.0.1", 0, Map.of("proxy", secret), LOG);
        open.add(broker);
        DirectBus pretender = server(broker, "proxy", secret);
        List<Frame> heardByBroker = new CopyOnWriteArrayList<>();
        broker.subscribe("route", heardByBroker::add);

        pretender.publish("route", text("i am the proxy"));

        Thread.sleep(1000);
        assertThat(broker.connected()).isEmpty();
        assertThat(heardByBroker).isEmpty();
    }

    @Test
    void theProxyNeverHearsAFrameItSentToItself() throws Exception {
        DirectBroker broker = broker();
        List<Frame> heard = new CopyOnWriteArrayList<>();
        broker.subscribe("route", heard::add);

        broker.send(DirectBroker.PROXY, "route", text("echo"));

        Thread.sleep(200);
        assertThat(heard).isEmpty();
    }

    @Test
    void aFramePublishedJustBeforeClosingStillGoesOut() throws Exception {
        DirectBroker broker = broker();
        DirectBus lobby = new DirectBus("lobby", "127.0.0.1", broker.port(), lobbySecret, LOG);
        DirectBus survival = server(broker, "survival", survivalSecret);
        List<Frame> heard = new CopyOnWriteArrayList<>();
        survival.subscribe("presence", heard::add);
        await(() -> broker.connected().containsAll(List.of("lobby", "survival")));

        lobby.publish("presence", text("leaving"));
        lobby.close();

        await(() -> !heard.isEmpty());
    }
}
