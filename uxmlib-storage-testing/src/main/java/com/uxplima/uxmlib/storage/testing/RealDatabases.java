package com.uxplima.uxmlib.storage.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.uxplima.uxmlib.storage.sql.Backend;
import com.uxplima.uxmlib.storage.sql.StorageSettings;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One empty database on each backend, with the servers started in containers the first time a test asks.
 *
 * <p>A server starts once for the whole test run and every test gets a database of its own on it, so tests
 * never see each other's rows and a run pays for three containers, not three per test. The containers go when
 * the run ends. The folder each database is handed is deleted when its test is over, since JUnit closes an
 * argument that can be closed.
 */
public final class RealDatabases implements ArgumentsProvider {

    /** The tag every {@link EveryBackend} test carries. */
    public static final String TAG = "real-database";

    /** The table prefix a database is handed out with. */
    public static final String TABLE_PREFIX = "t_";

    private static final String ROOT_PASSWORD = "root";
    private static final int STARTUP_ATTEMPTS = 3;
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static final Map<Backend, JdbcDatabaseContainer<?>> SERVERS = new EnumMap<>(Backend.class);

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Arrays.stream(Backend.values()).map(backend -> Arguments.of(Named.of(backend.id(), fresh(backend))));
    }

    /** An empty database on {@code backend}, under {@link #TABLE_PREFIX}. */
    public static RealDatabase fresh(Backend backend) {
        Objects.requireNonNull(backend, "backend");
        Path folder = temporaryFolder();
        if (backend.isFile()) {
            return new RealDatabase(settings(backend, "localhost", 0, "rows", "", ""), folder);
        }
        JdbcDatabaseContainer<?> server = server(backend);
        String name = "t" + NEXT.incrementAndGet();
        create(server, name);
        return new RealDatabase(
                settings(
                        backend,
                        server.getHost(),
                        server.getFirstMappedPort(),
                        name,
                        server.getUsername(),
                        server.getPassword()),
                folder);
    }

    private static StorageSettings settings(
            Backend backend, String host, int port, String database, String username, String password) {
        return new StorageSettings(
                backend,
                "rows.db",
                host,
                backend.isFile() ? backend.defaultPort() : port,
                database,
                username,
                password,
                TABLE_PREFIX,
                4,
                false);
    }

    private static synchronized JdbcDatabaseContainer<?> server(Backend backend) {
        return SERVERS.computeIfAbsent(backend, wanted -> {
            // A busy host can hand Docker a port that another process takes before the container binds it.
            // That start fails and the next one does not, so a start is tried more than once.
            JdbcDatabaseContainer<?> started = container(wanted).withStartupAttempts(STARTUP_ATTEMPTS);
            started.start();
            return started;
        });
    }

    @SuppressWarnings("resource") // the container lives for the whole run and is reaped when it ends
    private static JdbcDatabaseContainer<?> container(Backend backend) {
        return switch (backend) {
            case MYSQL ->
                new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                        .withUsername("root")
                        .withPassword(ROOT_PASSWORD);
            case MARIADB ->
                new MariaDBContainer<>(DockerImageName.parse("mariadb:11.4"))
                        .withUsername("root")
                        .withPassword(ROOT_PASSWORD);
            case POSTGRESQL -> new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.6-trixie"));
            case SQLITE -> throw new IllegalArgumentException("a file needs no server");
        };
    }

    private static void create(JdbcDatabaseContainer<?> server, String name) {
        try (Connection connection =
                        DriverManager.getConnection(server.getJdbcUrl(), server.getUsername(), server.getPassword());
                Statement statement = connection.createStatement()) {
            // The name is ours, a letter and a counter, so it is safe to write into the statement.
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not create a database on " + server.getDockerImageName(), failure);
        }
    }

    private static Path temporaryFolder() {
        try {
            return Files.createTempDirectory("uxmlib-real-");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
