package com.uxplima.uxmlib.hook.edit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Asking for the edit guard on a server with no world editor links no WorldEdit class.
 *
 * <p>{@link EditGuard#forServer()} called {@code WorldEditGuard.find()}, and to run that the JVM links
 * {@code WorldEditGuard}, whose nested extent extends a WorldEdit class. On 2026-09-23 uxm-plots booted on a Paper
 * server without WorldEdit and failed to enable with {@code NoClassDefFoundError: com/sk89q/worldedit/extent/Extent}
 * before the presence check inside {@code find()} ever ran. {@link EditGuardTest} could not see it: WorldEdit is on
 * the test classpath, so everything links. This test loads the edit package through a class loader that cannot see
 * WorldEdit, which is what a server without it is.
 */
class AServerWithoutWorldEditLinksNoneOfItTest {

    private static final String EDIT_PACKAGE = EditGuard.class.getPackageName() + ".";

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the guard for a server without WorldEdit is none, and nothing of WorldEdit is linked to say so")
    void noWorldEditIsLinked() throws ReflectiveOperationException {
        ClassLoader withoutWorldEdit = new WithoutWorldEdit(getClass().getClassLoader());
        Class<?> seam = Class.forName(EDIT_PACKAGE + "EditGuard", true, withoutWorldEdit);
        Method forServer = seam.getMethod("forServer");

        Object guard;
        try {
            guard = forServer.invoke(null);
        } catch (InvocationTargetException failed) {
            throw new AssertionError("asking for the guard linked WorldEdit: " + failed.getCause(), failed.getCause());
        }

        assertThat(seam.getMethod("isAvailable").invoke(guard)).isEqualTo(Boolean.FALSE);
        assertThat(seam.getMethod("pluginName").invoke(guard)).isEqualTo("none");
    }

    /**
     * Loads the edit package itself, so its classes link against what this loader can see, and refuses every
     * WorldEdit class. Everything else, Bukkit and the rest of the library included, comes from the parent, so the
     * mocked server the test started is the one the guard asks.
     */
    private static final class WithoutWorldEdit extends ClassLoader {

        WithoutWorldEdit(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("com.sk89q.worldedit.") || name.startsWith("com.fastasyncworldedit.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(EDIT_PACKAGE)) {
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
