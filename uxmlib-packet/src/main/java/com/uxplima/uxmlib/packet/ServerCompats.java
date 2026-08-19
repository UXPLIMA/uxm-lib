package com.uxplima.uxmlib.packet;

import java.util.Objects;

import com.uxplima.uxmlib.common.ServerVersion;
import com.uxplima.uxmlib.packet.compat.ServerCompat;
import com.uxplima.uxmlib.packet.compat.mc1_21.Mc1_21ServerCompat;
import com.uxplima.uxmlib.packet.compat.mc26.Mc26ServerCompat;

/**
 * Picks the {@link ServerCompat} matching the server this library was loaded on.
 *
 * <p>Every supported line ships its own adapter artifact, all of them on the classpath at once. Only the one
 * chosen here is ever loaded: the others are named from a single branch that the running server never takes,
 * and the JVM resolves a class the first time a branch executes, not when this class is verified.
 *
 * <p>The newest adapter also serves anything newer than itself. A Minecraft line that moves one of these
 * internals again fails loudly on the call rather than silently doing the wrong thing, and the fix is a new
 * adapter artifact plus one branch below.
 */
public final class ServerCompats {

    private ServerCompats() {}

    /** The adapter for the running server, resolved on first use and held for the life of the JVM. */
    public static ServerCompat current() {
        return Holder.INSTANCE;
    }

    /** The server lines uxmLib ships an adapter for, oldest first. */
    enum Line {
        MC1_21,
        MC26
    }

    /** Which adapter a given server version calls for. Package-private so the choice itself is testable. */
    static Line lineOf(ServerVersion version) {
        Objects.requireNonNull(version, "version");
        return version.isAtLeast(26, 0) ? Line.MC26 : Line.MC1_21;
    }

    static ServerCompat adapterFor(Line line) {
        Objects.requireNonNull(line, "line");
        return switch (line) {
            case MC1_21 -> new Mc1_21ServerCompat();
            case MC26 -> new Mc26ServerCompat();
        };
    }

    // Holder idiom: the server version is read the first time an adapter is asked for. Reading it in a static
    // initialiser instead would tie class loading to Bukkit being up.
    private static final class Holder {
        private static final ServerCompat INSTANCE = adapterFor(lineOf(ServerVersion.current()));
    }
}
