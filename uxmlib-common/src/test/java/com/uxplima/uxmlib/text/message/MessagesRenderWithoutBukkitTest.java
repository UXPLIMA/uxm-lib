package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * A message renders where there is no Bukkit, which is every proxy. Naming the viewer in {@code <player>} reached for
 * the Bukkit player type on every render, and on a proxy that type does not exist: each line a Velocity plugin drew
 * threw {@code NoClassDefFoundError}. The render here runs in a class loader that refuses every Bukkit class.
 */
class MessagesRenderWithoutBukkitTest {

    @Test
    void aMessageRendersWhereBukkitIsAbsent() throws Exception {
        try (URLClassLoader noBukkit = new NoBukkit(classpath())) {
            Class<?> probe = noBukkit.loadClass(RenderWithoutBukkit.class.getName());
            Method greet = probe.getMethod("greet");

            assertThat(greet.invoke(null)).asString().startsWith("Hello");
        }
    }

    private static URL[] classpath() throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator), -1)) {
            if (entry.isEmpty()) {
                continue;
            }
            urls.add(new File(entry).toURI().toURL());
        }
        return urls.toArray(new URL[0]);
    }

    /** Loads everything from the test classpath itself, except Bukkit, which it says is not there. */
    private static final class NoBukkit extends URLClassLoader {

        NoBukkit(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.bukkit.")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
