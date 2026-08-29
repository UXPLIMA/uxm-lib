package com.uxplima.uxmlib.hologram;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Resolves a player name to its base64 skin-texture blob through the public Mojang API, off the main
 * thread. Two calls chain: the name endpoint yields the account UUID, then the session server yields the
 * profile whose {@code textures} property is the base64 value a {@link PlayerHeads} head wants. The result
 * is cached (positive and negative) so a repeated lookup never hits the network, and a {@code 429} is
 * retried after a short back-off.
 *
 * <p>Native and non-blocking: the JDK {@link HttpClient} runs the request on its own executor and the
 * returned {@link CompletableFuture} completes off-thread, so no server (or Folia region) thread is ever
 * parked on I/O. Apply the result back on the entity's region thread via your {@code Scheduler}. Unknown
 * names, empty profiles, and exhausted retries all complete with {@link Optional#empty()} rather than throw.
 */
public final class MojangSkinResolver {

    private static final String NAME_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_ENDPOINT = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(2);
    // One retry covers a transient rate-limit beat without risking a request storm against Mojang.
    private static final int DEFAULT_RETRIES = 1;
    private static final int CACHE_SIZE = 1024;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final Function<URI, CompletableFuture<HttpStatusBody>> transport;
    private final SkinCache cache;
    private final Duration backoff;
    private final int retries;

    /** A resolver backed by a fresh shared {@link HttpClient} and a 30-minute, 1024-entry cache. */
    public MojangSkinResolver() {
        this(jdkTransport(defaultClient()), new SkinCache(CACHE_SIZE, CACHE_TTL, System::nanoTime));
    }

    MojangSkinResolver(Function<URI, CompletableFuture<HttpStatusBody>> transport, SkinCache cache) {
        this(transport, cache, DEFAULT_BACKOFF, DEFAULT_RETRIES);
    }

    MojangSkinResolver(
            Function<URI, CompletableFuture<HttpStatusBody>> transport,
            SkinCache cache,
            Duration backoff,
            int retries) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        if (retries < 0) {
            throw new IllegalArgumentException("retries must be >= 0");
        }
        this.retries = retries;
    }

    /**
     * Resolve {@code name} to its base64 texture blob, off-thread. The future yields the texture, or empty
     * when the name has no profile (or the lookup was rate-limited past its retries). Cached results: both
     * a hit and a known-absent, short-circuit the network.
     */
    public CompletableFuture<Optional<String>> resolveTexture(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Optional<Optional<String>> cached = cache.lookup(name);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }
        return lookupUuid(name).thenCompose(uuid -> fetchTextureFor(name, uuid));
    }

    private CompletableFuture<Optional<String>> fetchTextureFor(String name, Optional<String> uuid) {
        if (uuid.isEmpty()) {
            cache.putAbsent(name);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return fetchTexture(uuid.get()).thenApply(texture -> store(name, texture));
    }

    private Optional<String> store(String name, Optional<String> texture) {
        texture.ifPresentOrElse(value -> cache.put(name, value), () -> cache.putAbsent(name));
        return texture;
    }

    private CompletableFuture<Optional<String>> lookupUuid(String name) {
        URI uri = URI.create(NAME_ENDPOINT + encodePath(name));
        return getWithRetry(uri, retries).thenApply(MojangSkinResolver::parseUuid);
    }

    private CompletableFuture<Optional<String>> fetchTexture(String uuid) {
        URI uri = URI.create(PROFILE_ENDPOINT + uuid);
        return getWithRetry(uri, retries).thenApply(MojangSkinResolver::parseTexture);
    }

    /** A GET that retries once on a {@code 429} (or Mojang's {@code 403} throttle) after a short back-off. */
    private CompletableFuture<HttpStatusBody> getWithRetry(URI uri, int retriesLeft) {
        return transport.apply(uri).thenCompose(response -> {
            if (!isThrottled(response.status()) || retriesLeft <= 0) {
                return CompletableFuture.completedFuture(response);
            }
            // A pure off-thread wait, not Bukkit scheduling: the resend fires once the back-off window
            // elapses, so no server (or Folia region) thread is ever parked on the rate-limit wait.
            Executor later = CompletableFuture.delayedExecutor(backoff.toMillis(), TimeUnit.MILLISECONDS);
            return CompletableFuture.completedFuture((Void) null)
                    .thenComposeAsync(ignored -> getWithRetry(uri, retriesLeft - 1), later);
        });
    }

    private static boolean isThrottled(int status) {
        return status == 429 || status == 403;
    }

    // A 204 (empty body) or 404 means the name has no account; only a 200 carries a usable id.
    private static Optional<String> parseUuid(HttpStatusBody response) {
        if (response.status() != 200 || response.body().isBlank()) {
            return Optional.empty();
        }
        return jsonString(response.body(), "id");
    }

    // The session-server profile carries the base64 blob as the value of its "textures" property.
    private static Optional<String> parseTexture(HttpStatusBody response) {
        if (response.status() != 200 || response.body().isBlank()) {
            return Optional.empty();
        }
        return jsonString(response.body(), "value");
    }

    // Pull the first string value for the given key out of a flat JSON object. Mojang's payloads are small
    // and stable, so a targeted scan beats dragging in a JSON parser for two fields.
    private static Optional<String> jsonString(String json, String key) {
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) {
            return Optional.empty();
        }
        int open = json.indexOf('"', json.indexOf(':', at) + 1);
        if (open < 0) {
            return Optional.empty();
        }
        int close = json.indexOf('"', open + 1);
        return close < 0 ? Optional.empty() : Optional.of(json.substring(open + 1, close));
    }

    // Player names are [A-Za-z0-9_], so they need no percent-encoding; still strip anything path-breaking.
    private static String encodePath(String name) {
        return name.replace("/", "").replace("?", "").replace("#", "");
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static Function<URI, CompletableFuture<HttpStatusBody>> jdkTransport(HttpClient client) {
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", "uxmLib-MojangSkinResolver (+https://github.com/UXPLIMA/uxmLib)")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> new HttpStatusBody(response.statusCode(), response.body()));
        };
    }
}
