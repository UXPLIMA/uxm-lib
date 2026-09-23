package com.uxplima.uxmlib.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Every way a plugin asks for a hook works on a server that has none of the plugins the hooks are for.
 *
 * <p>A hook is only worth having if asking for it is safe where its plugin is absent. On 2026-09-23 uxm-plots failed
 * to enable on a Paper server without WorldEdit: the edit seam linked a class that extends a WorldEdit type before
 * its own presence check could run. The tests of each hook could not see that, because every one of these plugins is
 * on the test classpath and everything links. This loads the hook and claim packages through a class loader that
 * refuses every one of those plugins, which is what a bare server is, and asks each hook the way a plugin does.
 */
class EveryHookLinksWithoutItsPluginTest {

    private static final String HOOKS = "com.uxplima.uxmlib.hook.";

    private static final String CLAIMS = "com.uxplima.uxmlib.claim.";

    /** Every package of a plugin some hook in this module is written against. */
    private static final List<String> ABSENT = List.of(
            "com.sk89q.",
            "com.fastasyncworldedit.",
            "me.clip.placeholderapi.",
            "net.luckperms.",
            "net.milkbowl.vault",
            "com.palmergames.",
            "me.angeschossen.",
            "me.ryanhamshire.",
            "fr.xyness.",
            "net.weesli.");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("every hook is asked for on a bare server without linking the plugin it is for")
    void everyHookLinks() throws ReflectiveOperationException {
        ClassLoader bare = new BareServer(getClass().getClassLoader());
        List<String> broke = new ArrayList<>();

        ask(bare, broke, "edit.EditGuard", "forServer");
        ask(bare, broke, "economy.VaultEconomy", "find");
        ask(bare, broke, "economy.VaultUnlockedEconomy", "find");
        ask(bare, broke, "permission.VaultPermission", "find");
        ask(bare, broke, "permission.LuckPermsHook", "find");
        ask(bare, broke, "region.TownyRegionService", "find");
        ask(bare, broke, "region.WorldGuardRegionService", "find");
        Class<?> registry = Class.forName(HOOKS + "placeholder.PlaceholderRegistry", true, bare);
        Object empty = registry.getConstructor().newInstance();
        call(
                broke,
                "placeholder.PlaceholderExpansions.register",
                Class.forName(HOOKS + "placeholder.PlaceholderExpansions", true, bare)
                        .getMethod("register", String.class, registry, String.class, String.class),
                "test",
                empty,
                "author",
                "1");

        assertThat(broke)
                .describedAs("each of these fails to enable a plugin on a server without its hook")
                .isEmpty();
    }

    private static void ask(ClassLoader bare, List<String> broke, String type, String method)
            throws ReflectiveOperationException {
        Method entry;
        try {
            entry = Class.forName(HOOKS + type, true, bare).getMethod(method);
        } catch (LinkageError linking) {
            broke.add(type + " did not load: " + linking);
            return;
        }
        call(broke, type + "." + method, entry);
    }

    private static void call(List<String> broke, String name, Method entry, Object... arguments)
            throws IllegalAccessException {
        try {
            entry.invoke(null, arguments);
        } catch (InvocationTargetException failed) {
            if (failed.getCause() instanceof LinkageError linking) {
                broke.add(name + " linked " + linking.getMessage());
            } else {
                throw new AssertionError(name + " threw " + failed.getCause(), failed.getCause());
            }
        }
    }

    /** Loads the hook and claim packages itself, and refuses every plugin a hook is written against. */
    private static final class BareServer extends ClassLoader {

        BareServer(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                for (String absent : ABSENT) {
                    if (name.startsWith(absent)) {
                        throw new ClassNotFoundException(name);
                    }
                }
                if (!name.startsWith(HOOKS) && !name.startsWith(CLAIMS)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineHere(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private Class<?> defineHere(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream bytes = getParent().getResourceAsStream(resource)) {
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] code = bytes.readAllBytes();
                return defineClass(name, code, 0, code.length);
            } catch (IOException unreadable) {
                throw new ClassNotFoundException(name, unreadable);
            }
        }
    }
}
