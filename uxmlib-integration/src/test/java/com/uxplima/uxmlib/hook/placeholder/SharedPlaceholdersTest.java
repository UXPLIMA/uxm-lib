package com.uxplima.uxmlib.hook.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Two plugins, each with its own copy of uxmLib in a class loader of its own as relocation leaves them, meeting in the
 * one {@code uxm} expansion of a PlaceholderAPI they share. Only JDK, Bukkit and PlaceholderAPI types cross between
 * them, as on a server.
 */
class SharedPlaceholdersTest {

    private final FakePlaceholderApi papi = new FakePlaceholderApi();
    private final List<String> logged = new ArrayList<>();
    private final List<PluginCopy> plugins = new ArrayList<>();

    @AfterEach
    void closeLoaders() throws IOException {
        for (PluginCopy plugin : plugins) {
            plugin.loader.close();
        }
    }

    @Test
    void twoPluginsAnswerSideBySideUnderOneExpansion() {
        PluginCopy essentials = plugin("Essentials");
        PluginCopy chat = plugin("Chat");

        assertThat(essentials.join("essentials", (player, path) -> "e:" + path)).isTrue();
        assertThat(chat.join("chat", (player, path) -> "c:" + path)).isTrue();

        assertThat(papi.answer("essentials_balance")).isEqualTo("e:balance");
        assertThat(papi.answer("chat_channel")).isEqualTo("c:channel");
        assertThat(papi.answer("skyblock_level")).as("no plugin holds it").isNull();
        assertThat(papi.registrations)
                .as("the second plugin joined rather than replaced")
                .hasValue(1);
    }

    @Test
    void theOthersKeepAnsweringWhenThePluginThatRegisteredItGoesFirst() {
        PluginCopy essentials = plugin("Essentials");
        PluginCopy chat = plugin("Chat");
        essentials.join("essentials", (player, path) -> "e:" + path);
        chat.join("chat", (player, path) -> "c:" + path);

        essentials.disabled(essentials);
        chat.disabled(essentials);

        assertThat(papi.answer("chat_channel")).isEqualTo("c:channel");
        assertThat(papi.answer("essentials_balance")).isNull();
        assertThat(papi.heldCodeIs(chat))
                .as("no code of the plugin that went is left answering")
                .isTrue();
    }

    @Test
    void theOthersKeepAnsweringWhenTheyHearOfItBeforeThePluginThatGoes() {
        PluginCopy essentials = plugin("Essentials");
        PluginCopy chat = plugin("Chat");
        essentials.join("essentials", (player, path) -> "e:" + path);
        chat.join("chat", (player, path) -> "c:" + path);

        chat.disabled(essentials);
        essentials.disabled(essentials);

        assertThat(papi.answer("chat_channel")).isEqualTo("c:channel");
        assertThat(papi.answer("essentials_balance")).isNull();
        assertThat(papi.heldCodeIs(chat)).isTrue();
    }

    @Test
    void aPluginThatGoesLeavesAndTheLastTakesTheExpansionDown() {
        PluginCopy essentials = plugin("Essentials");
        PluginCopy chat = plugin("Chat");
        essentials.join("essentials", (player, path) -> "e:" + path);
        chat.join("chat", (player, path) -> "c:" + path);

        essentials.disabled(chat);
        chat.disabled(chat);
        assertThat(papi.answer("chat_channel")).isNull();
        assertThat(papi.answer("essentials_balance")).isEqualTo("e:balance");

        essentials.disabled(essentials);
        assertThat(papi.held.get()).isNull();
    }

    @Test
    void aSecondClaimOnAPrefixIsRefusedAndTheFirstKeepsAnswering() {
        PluginCopy essentials = plugin("Essentials");
        PluginCopy impostor = plugin("Impostor");
        essentials.join("essentials", (player, path) -> "e:" + path);

        assertThat(impostor.join("essentials", (player, path) -> "i:" + path)).isFalse();

        assertThat(papi.answer("essentials_balance")).isEqualTo("e:balance");
        assertThat(logged)
                .anySatisfy(line ->
                        assertThat(line).contains("%uxm_essentials_...%").contains("held by Essentials"));
    }

    @Test
    void anExpansionThatIsNotTheSharedOneIsLeftInPlace() {
        PlaceholderExpansion foreign = new ForeignExpansion();
        papi.held.set(foreign);

        assertThat(plugin("Essentials").join("essentials", (player, path) -> "e:" + path))
                .isFalse();

        assertThat(papi.held.get()).isSameAs(foreign);
        assertThat(logged).anySatisfy(line -> assertThat(line).contains("not the shared UXM one"));
    }

    @Test
    void anExpansionPlaceholderApiLostIsRegisteredAgainOnce() {
        PluginCopy essentials = plugin("Essentials");
        PluginCopy chat = plugin("Chat");
        essentials.join("essentials", (player, path) -> "e:" + path);
        chat.join("chat", (player, path) -> "c:" + path);
        papi.held.set(null);

        essentials.expansionsLoaded();
        chat.expansionsLoaded();

        assertThat(papi.answer("essentials_balance")).isEqualTo("e:balance");
        assertThat(papi.answer("chat_channel")).isEqualTo("c:channel");
        assertThat(papi.registrations).hasValue(2);
    }

    @Test
    void theRelationalFormGoesToThePrefixAndAFailureIsBlank() {
        PluginCopy chat = plugin("Chat");
        chat.join(
                "chat",
                (player, path) -> {
                    throw new IllegalStateException("broken");
                },
                (viewer, target, path) -> "rel:" + path);
        Player alex = unasked(Player.class);

        assertThat(papi.answer("chat_channel")).isEmpty();
        assertThat(((Relational) papi.expansion()).onPlaceholderRequest(alex, alex, "chat_can_see"))
                .isEqualTo("rel:can_see");
    }

    @Test
    void aPrefixIsOneWord() {
        PlaceholderRegistry registry = new PlaceholderRegistry();

        assertThatThrownBy(() -> SharedPlaceholders.join(unasked(Plugin.class), "my_plugin", registry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SharedPlaceholders.join(unasked(Plugin.class), "Chat", registry))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PluginCopy plugin(String name) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        PluginCopy copy = new PluginCopy(name, papi, log);
        plugins.add(copy);
        return copy;
    }

    /** A server object that is only handed on: asking it anything fails the test. */
    private static <T> T unasked(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                SharedPlaceholdersTest.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                }));
    }

    /** One expansion per identifier, the later replacing the earlier, as PlaceholderAPI keeps them. */
    private static final class FakePlaceholderApi {

        final AtomicReference<@Nullable PlaceholderExpansion> held = new AtomicReference<>();
        final AtomicInteger registrations = new AtomicInteger();
        final Supplier<@Nullable PlaceholderExpansion> current = held::get;
        final Predicate<PlaceholderExpansion> register = expansion -> {
            registrations.incrementAndGet();
            held.set(expansion);
            return true;
        };
        final Consumer<PlaceholderExpansion> unregister = expansion -> held.compareAndSet(expansion, null);

        PlaceholderExpansion expansion() {
            @Nullable PlaceholderExpansion expansion = held.get();
            if (expansion == null) {
                throw new AssertionError("no expansion is registered");
            }
            return expansion;
        }

        @Nullable String answer(String params) {
            return expansion().onRequest(null, params);
        }

        boolean heldCodeIs(PluginCopy plugin) {
            return expansion().getClass().getClassLoader() == plugin.loader;
        }
    }

    /** A plugin's own copy of this module, loaded apart from every other plugin's as a relocated shade is. */
    private static final class PluginCopy {

        private final String name;
        private final URLClassLoader loader;
        private final Object table;

        PluginCopy(String name, FakePlaceholderApi papi, Logger log) {
            this.name = name;
            this.loader = new OwnCopyLoader(
                    SharedTable.class.getProtectionDomain().getCodeSource().getLocation());
            try {
                Class<?> type = loader.loadClass(SharedTable.class.getName());
                Constructor<?> create = type.getDeclaredConstructor(
                        String.class, String.class, Supplier.class, Predicate.class, Consumer.class, Logger.class);
                create.setAccessible(true);
                this.table = create.newInstance(name, "1.0", papi.current, papi.register, papi.unregister, log);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }

        boolean join(String prefix, BiFunction<@Nullable OfflinePlayer, String, @Nullable String> answer) {
            return join(prefix, answer, (viewer, target, path) -> null);
        }

        boolean join(
                String prefix,
                BiFunction<@Nullable OfflinePlayer, String, @Nullable String> answer,
                Relational relational) {
            return (Boolean) call(
                    "join",
                    new Class<?>[] {String.class, BiFunction.class, Relational.class},
                    prefix,
                    answer,
                    relational);
        }

        /** Hear that {@code going} is being disabled, as every plugin does. */
        void disabled(PluginCopy going) {
            call("pluginDisabled", new Class<?>[] {String.class, ClassLoader.class}, going.name, going.loader);
        }

        void expansionsLoaded() {
            call("expansionsLoaded", new Class<?>[0]);
        }

        private Object call(String method, Class<?>[] types, Object... args) {
            try {
                Method found = table.getClass().getDeclaredMethod(method, types);
                found.setAccessible(true);
                return found.invoke(table, args);
            } catch (InvocationTargetException failure) {
                throw new IllegalStateException(failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    /** Loads this module's classes itself and leaves everything else, the server's types, to the parent. */
    private static final class OwnCopyLoader extends URLClassLoader {

        OwnCopyLoader(URL classes) {
            super(new URL[] {classes}, SharedPlaceholdersTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith("com.uxplima.uxmlib.")) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notHere) {
                        loaded = null;
                    }
                }
                if (loaded == null) {
                    return super.loadClass(name, resolve);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }

    /** An expansion another plugin registered as {@code uxm} on its own. */
    private static final class ForeignExpansion extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "uxm";
        }

        @Override
        public String getAuthor() {
            return "Someone";
        }

        @Override
        public String getVersion() {
            return "0.1";
        }
    }
}
