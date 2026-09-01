package com.uxplima.uxmlib.backup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

/**
 * The seam between a plugin that holds state in memory and whatever is about to copy that state off the disk.
 *
 * <p>A plugin that saves every five minutes has, at any moment, up to five minutes of play that a file copy
 * would miss. A plugin that registers here is told first, writes what it holds, and the copy that follows is
 * whole. Nothing about a backup tool is assumed: this is a request to save, and the caller may be a backup
 * plugin, a restart plugin or a command an operator typed.
 *
 * <h2>Why the contract is a plain {@link Runnable}</h2>
 *
 * <p>Every plugin of ours shades uxmLib and relocates it into its own namespace, so
 * {@code com.uxplima.a.libs.uxmlib.X} and {@code com.uxplima.b.libs.uxmlib.X} are two unrelated classes. An
 * interface of our own could therefore never be passed from one plugin to another. {@code Runnable} comes
 * from the boot class loader, so every plugin on the server sees the same type, whatever it relocated.
 *
 * <p>The registration is marked, and only marked registrations are ever run. A plugin that registers a
 * {@code Runnable} service for a purpose of its own is left alone.
 */
public final class BackupParticipants {

    private static final String MARK = "uxmlib:backup-participant:";

    private BackupParticipants() {}

    /**
     * Ask to be told before the files are read.
     *
     * <p>{@code flushToDisk} must write what the plugin holds and return. It may be run on any thread the
     * caller chooses, so it does what a save does and nothing more: no message to a player, no event, no
     * wait on another plugin.
     */
    public static void register(Plugin plugin, Runnable flushToDisk) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(flushToDisk, "flushToDisk");
        Bukkit.getServicesManager()
                .register(Runnable.class, new Marked(plugin.getName(), flushToDisk), plugin, ServicePriority.Normal);
    }

    /** Stop being told. A plugin that is disabled is unregistered by the server anyway. */
    public static void unregister(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (RegisteredServiceProvider<Runnable> registered : registrations()) {
            if (registered.getPlugin().equals(plugin)) {
                Bukkit.getServicesManager().unregister(Runnable.class, registered.getProvider());
            }
        }
    }

    /** The plugins that are listening, by name, for a status line. */
    public static List<String> listening() {
        List<String> names = new ArrayList<>();
        for (RegisteredServiceProvider<Runnable> registered : registrations()) {
            names.add(registered.getPlugin().getName());
        }
        return List.copyOf(names);
    }

    /**
     * Tell everyone to save, and wait no longer than {@code timeout}.
     *
     * <p>Each participant runs on {@code where}, because only the caller knows which thread a save is safe on:
     * a plugin that reads an online player's inventory needs the server thread, and a plugin that only closes
     * a file does not. The wait is bounded, so one plugin that will not finish cannot hold a backup open.
     *
     * @return the plugins that had not finished when the time ran out, in registration order
     */
    public static List<String> prepareAll(Duration timeout, Executor where) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(where, "where");

        Map<String, CompletableFuture<Void>> running = new LinkedHashMap<>();
        for (RegisteredServiceProvider<Runnable> registered : registrations()) {
            Runnable participant = registered.getProvider();
            running.put(registered.getPlugin().getName(), CompletableFuture.runAsync(participant, where));
        }
        // Waited for one by one against a single deadline, rather than through allOf, which cannot be
        // written without a raw array of futures.
        long deadline = System.nanoTime() + timeout.toNanos();
        for (CompletableFuture<Void> future : running.values()) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                break;
            }
            try {
                future.get(left, TimeUnit.NANOSECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception lateOrFailed) {
                // Which plugin it was is read from the futures below, so nothing is lost here.
            }
        }

        List<String> late = new ArrayList<>();
        running.forEach((name, future) -> {
            if (!future.isDone() || future.isCompletedExceptionally()) {
                late.add(name);
            }
        });
        return List.copyOf(late);
    }

    private static List<RegisteredServiceProvider<Runnable>> registrations() {
        List<RegisteredServiceProvider<Runnable>> ours = new ArrayList<>();
        for (RegisteredServiceProvider<Runnable> registered :
                Bukkit.getServicesManager().getRegistrations(Runnable.class)) {
            if (registered.getProvider().toString().toLowerCase(Locale.ROOT).startsWith(MARK)) {
                ours.add(registered);
            }
        }
        return ours;
    }

    /**
     * A participant, wrapped so that it can be told apart from any other {@code Runnable} service on the
     * server. The mark is in {@code toString}, because that is the one thing a plain {@code Runnable} can
     * carry across two class loaders that share no type of ours.
     */
    private record Marked(String plugin, Runnable flushToDisk) implements Runnable {

        @Override
        public void run() {
            flushToDisk.run();
        }

        @Override
        public String toString() {
            return MARK + plugin;
        }
    }
}
