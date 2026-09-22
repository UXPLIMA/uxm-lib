package com.uxplima.uxmlib.update;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A release endpoint that has moved must still answer.
 *
 * <p>GitHub keeps a redirect when a repository is renamed, and both of ours have been renamed: uxmLib to
 * uxm-lib, and the essentials product to uxm-essentials. A checker pointed at the old path is answered 301
 * rather than 200, and the client here folded every non-2xx status to "no release", so the check reported
 * nothing forever and reported it quietly. That is the shape of the defect: not an error an operator sees,
 * but a feature that is on, costs a request twice a day, and can never succeed.
 *
 * <p>The server is on the loopback address and the redirect is plain HTTP on both legs, which is what
 * {@code Redirect.NORMAL} follows; it declines an HTTPS to HTTP downgrade, which is the one case where
 * following would be worse than failing.
 */
class HttpRedirectTest {

    private static final String BODY = "{\"tag_name\":\"1.2.3\"}";

    @Test
    @DisplayName("an endpoint that answers 301 is followed to the body")
    void aMovedEndpointIsFollowed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/old", exchange -> {
            exchange.getResponseHeaders().add("Location", "/new");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/new", HttpRedirectTest::respondWithTheBody);
        server.start();
        try {
            URI moved = URI.create("http://" + server.getAddress().getHostString() + ":"
                    + server.getAddress().getPort() + "/old");

            Optional<String> answered = Http.getJson(moved, Optional::of).get(20, TimeUnit.SECONDS);

            assertThat(answered)
                    .as("a renamed repository answers 301, and a checker that does not follow it never sees a "
                            + "release")
                    .contains(BODY);
        } finally {
            server.stop(0);
        }
    }

    private static void respondWithTheBody(HttpExchange exchange) throws IOException {
        byte[] body = BODY.getBytes(UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
