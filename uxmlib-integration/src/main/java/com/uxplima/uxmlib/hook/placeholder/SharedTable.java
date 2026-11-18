package com.uxplima.uxmlib.hook.placeholder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.jspecify.annotations.Nullable;

/**
 * One plugin's place in the shared {@code uxm} expansion: the prefixes it joined, and the part it plays when the
 * plugin that registered the expansion goes away.
 *
 * <p>Every plugin shades its own uxmLib, relocated, so no uxmLib class is the same class in two plugins and none can
 * be looked up or cast across them. What two plugins meet on is built from types they all load from one place: the
 * JDK, Bukkit and PlaceholderAPI. The contract, which every uxmLib version keeps:
 *
 * <ul>
 *   <li>The expansion PlaceholderAPI holds as {@code uxm} implements {@link Supplier}, and gives the member table, a
 *       {@link ConcurrentMap} from prefix to member. The table is the one object every plugin shares, and changes to
 *       it are made while holding its monitor.
 *   <li>A member is a {@link PlaceholderExpansion} that is never registered itself: its identifier is the prefix, its
 *       {@link PlaceholderExpansion#getRequiredPlugin()} the plugin that joined, and its {@code onRequest} answers
 *       what follows {@code <prefix>_}. A member that is {@link Relational} answers the relational form too.
 * </ul>
 *
 * <p>The table outlives any one plugin. The plugin that registered the expansion leaves it in place when it goes,
 * and each plugin that stays drops the members of the one going and registers an expansion of its own over the same
 * table when the one in place belongs to the plugin going. The first to do so wins; the others find it in place. A
 * PlaceholderAPI that lost the expansion, through its own reload, has it registered again the same way.
 *
 * <p>The seams to PlaceholderAPI are JDK functions, so a test can stand two plugins up in two class loaders over one
 * fake PlaceholderAPI.
 */
final class SharedTable {

    /** The identifier every plugin shares, giving {@code %uxm_<prefix>_<path>%}. */
    static final String IDENTIFIER = "uxm";

    private final String owner;
    private final String version;
    private final Supplier<@Nullable PlaceholderExpansion> current;
    private final Predicate<PlaceholderExpansion> register;
    private final Consumer<PlaceholderExpansion> unregister;
    private final Logger log;
    private final Map<String, PlaceholderExpansion> mine = new ConcurrentHashMap<>();
    private volatile @Nullable ConcurrentMap<String, PlaceholderExpansion> table;

    /**
     * @param owner the name of the plugin joining
     * @param version its version, shown by {@code /papi info uxm} while its expansion is the one in place
     * @param current the expansion PlaceholderAPI holds as {@code uxm}, or null
     * @param register registers an expansion with PlaceholderAPI, answering whether it took it
     * @param unregister takes an expansion off PlaceholderAPI
     */
    SharedTable(
            String owner,
            String version,
            Supplier<@Nullable PlaceholderExpansion> current,
            Predicate<PlaceholderExpansion> register,
            Consumer<PlaceholderExpansion> unregister,
            Logger log) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.version = Objects.requireNonNull(version, "version");
        this.current = Objects.requireNonNull(current, "current");
        this.register = Objects.requireNonNull(register, "register");
        this.unregister = Objects.requireNonNull(unregister, "unregister");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Answer {@code %uxm_<prefix>_...%} through {@code answer} and its relational form through {@code relational},
     * registering the shared expansion when there is none yet. Refused, with a line in the log, when another plugin
     * holds the prefix or the {@code uxm} identifier is held by an expansion that is not the shared one.
     */
    boolean join(
            String prefix,
            BiFunction<@Nullable OfflinePlayer, String, @Nullable String> answer,
            Relational relational) {
        Objects.requireNonNull(prefix, "prefix");
        PlaceholderExpansion member = new SharedMember(prefix, owner, version, answer, relational);
        @Nullable PlaceholderExpansion existing = current.get();
        ConcurrentMap<String, PlaceholderExpansion> shared;
        if (existing == null) {
            ConcurrentMap<String, PlaceholderExpansion> known = table;
            shared = known == null ? new ConcurrentHashMap<>() : known;
        } else {
            @Nullable ConcurrentMap<String, PlaceholderExpansion> found = tableOf(existing);
            if (found == null) {
                log.warning(foreign(prefix, existing));
                return false;
            }
            shared = found;
        }
        synchronized (shared) {
            if (!claim(shared, prefix, member)) {
                return false;
            }
            if (current.get() == null && !host(shared)) {
                shared.remove(prefix, member);
                return false;
            }
        }
        table = shared;
        mine.put(prefix, member);
        return true;
    }

    /**
     * Take this plugin's prefixes out of the table. The expansion stays registered while other plugins are in it, so
     * they can find the table; when this plugin's code goes, the ones staying register their own.
     */
    void leave() {
        ConcurrentMap<String, PlaceholderExpansion> shared = table;
        if (shared == null) {
            return;
        }
        synchronized (shared) {
            mine.forEach(
                    (prefix, member) -> shared.computeIfPresent(prefix, (key, held) -> held == member ? null : held));
            mine.clear();
            @Nullable PlaceholderExpansion now = current.get();
            if (now != null && shared.isEmpty() && tableOf(now) == shared) {
                unregister.accept(now);
            }
        }
    }

    /**
     * The plugin named {@code name}, whose classes {@code code} loaded, is going: its members leave, and when the
     * expansion in place is its code, this plugin registers its own over the same table.
     */
    void pluginDisabled(String name, ClassLoader code) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(code, "code");
        if (name.equalsIgnoreCase(owner)) {
            leave();
            return;
        }
        ConcurrentMap<String, PlaceholderExpansion> shared = table;
        if (shared == null || mine.isEmpty()) {
            return;
        }
        synchronized (shared) {
            shared.values().removeIf(member -> name.equalsIgnoreCase(member.getRequiredPlugin()));
            @Nullable PlaceholderExpansion now = current.get();
            if (now != null && now.getClass().getClassLoader() == code && tableOf(now) == shared) {
                unregister.accept(now);
                host(shared);
            }
        }
    }

    /**
     * PlaceholderAPI loaded its expansions again. When it lost the shared one, register it again; when another
     * table is in place, which only a plugin that lost the table could cause, move this plugin's members to it.
     */
    void expansionsLoaded() {
        ConcurrentMap<String, PlaceholderExpansion> shared = table;
        if (shared == null || mine.isEmpty()) {
            return;
        }
        @Nullable PlaceholderExpansion now = current.get();
        if (now == null) {
            synchronized (shared) {
                if (current.get() == null) {
                    host(shared);
                }
            }
            return;
        }
        @Nullable ConcurrentMap<String, PlaceholderExpansion> theirs = tableOf(now);
        if (theirs == null) {
            log.warning("The shared %uxm_...% expansion could not be registered again: " + held(now));
            return;
        }
        if (theirs != shared) {
            synchronized (theirs) {
                mine.entrySet().removeIf(entry -> !claim(theirs, entry.getKey(), entry.getValue()));
            }
            table = theirs;
        }
    }

    /** Put {@code member} under {@code prefix}, unless another plugin holds it. A plugin may take its own again. */
    private boolean claim(
            ConcurrentMap<String, PlaceholderExpansion> shared, String prefix, PlaceholderExpansion member) {
        @Nullable PlaceholderExpansion held = shared.putIfAbsent(prefix, member);
        if (held == null) {
            return true;
        }
        if (owner.equalsIgnoreCase(held.getRequiredPlugin())) {
            shared.put(prefix, member);
            return true;
        }
        log.warning("Not answering %uxm_" + prefix + "_...%: the prefix " + prefix + " is held by "
                + held.getRequiredPlugin() + ", which keeps answering it.");
        return false;
    }

    /** Register an expansion of this plugin's over {@code shared}, answering whether PlaceholderAPI took it. */
    private boolean host(ConcurrentMap<String, PlaceholderExpansion> shared) {
        boolean taken = register.test(new SharedDispatcher(shared, owner, version));
        if (!taken) {
            log.warning("PlaceholderAPI did not take the shared %uxm_...% expansion.");
        }
        return taken;
    }

    /** The member table of the shared expansion, or null when {@code expansion} is another one. */
    @SuppressWarnings("unchecked") // every member is a PlaceholderExpansion, the one type all plugins share
    static @Nullable ConcurrentMap<String, PlaceholderExpansion> tableOf(PlaceholderExpansion expansion) {
        if (expansion instanceof Supplier<?> supplier && supplier.get() instanceof ConcurrentMap<?, ?> shared) {
            return (ConcurrentMap<String, PlaceholderExpansion>) shared;
        }
        return null;
    }

    private static String foreign(String prefix, PlaceholderExpansion existing) {
        return "Not answering %uxm_" + prefix + "_...%: " + held(existing);
    }

    private static String held(PlaceholderExpansion existing) {
        return "the PlaceholderAPI identifier uxm is held by an expansion that is not the shared UXM one ("
                + existing.getAuthor() + ", version " + existing.getVersion() + ").";
    }
}
