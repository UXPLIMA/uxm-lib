/**
 * Building a menu from a config file. {@link com.uxplima.uxmlib.gui.config.MenuConfig} reads a HOCON node
 * (title, size, and per-slot icons) into a ready {@link com.uxplima.uxmlib.gui.SimpleGui}, so a server
 * owner re-skins a menu in a file while code only wires the click behaviour;
 * {@link com.uxplima.uxmlib.gui.config.MenuActions} maps the action names that appear in such a file to the
 * {@link com.uxplima.uxmlib.gui.GuiNavigator} verbs they trigger, and
 * {@link com.uxplima.uxmlib.gui.config.MenuConditions} maps the condition names a multi-state item
 * references to the per-viewer predicate that selects which state renders. A config-defined icon's display
 * text may be drawn from a lang file by key ({@code name-key}/{@code lore-key}) and resolved through an
 * {@link com.uxplima.uxmlib.text.message.MessageCatalog}; a stateful item's variants overlay their
 * differences on a base icon via {@link com.uxplima.uxmlib.gui.config.IconOptions#combine}; and a config
 * written against an older release is migrated to current key names in place on load (see
 * {@link com.uxplima.uxmlib.gui.config.MenuConfig#migrate}).
 *
 * <p>{@link com.uxplima.uxmlib.gui.config.MenuSpec} reads the other menu shape: numbered slots and slot
 * ranges rather than a mask, a draw priority, actions that run when the menu opens, a click block with the
 * four sides a chest offers, a view list that decides whether an item is drawn at all, and a computed list
 * that fills a slot range from a source the plugin registers. It is a value and a reader only: it names no
 * server type, so a layout is proved correct without one, and what an action or a source means stays with
 * the plugin that registers it.
 *
 * <p>{@link com.uxplima.uxmlib.gui.config.MenuAction} reads one line of that shape's {@code click} or
 * {@code open-actions} list, and {@link com.uxplima.uxmlib.gui.config.MenuActionRunner} performs it for one
 * viewer. Five verbs mean the same thing in every menu of every plugin ({@code close}, {@code open},
 * {@code command}, {@code message} and {@code sound}); anything else is a name a plugin registered with
 * {@link com.uxplima.uxmlib.gui.config.MenuActions#registerVerb}, written {@code <plugin>:<verb>} with the
 * rest of the line as its argument. A verb nobody registered is refused while the file loads, not on a
 * click, so an operator sees the typo at the moment they can fix it.
 *
 * <h2>Shading: keep the service files when minimizing</h2>
 *
 * This menu-config layer leans on libraries that locate their codecs through the JDK {@link
 * java.util.ServiceLoader}: Configurate finds its built-in {@code TypeSerializer}s and Adventure/MiniMessage
 * find their components through {@code META-INF/services/*} provider files. A {@link java.util.ServiceLoader}
 * lookup is reflective, so a consumer who shades this module with the Shadow plugin's {@code minimize()} can
 * have those provider files (and the classes they name) pruned as "unreachable", and config parsing then
 * fails at runtime with a missing-serializer error that never shows at build time. When minimizing, keep the
 * SPI resources and their backing classes, for example:
 *
 * <pre>{@code
 * tasks.shadowJar {
 *     minimize {
 *         // never strip the service-provider files the codecs are discovered through
 *         exclude("META-INF/services/**")
 *         // and keep the serializer/codec classes those files point at
 *         exclude(dependency("org.spongepowered:configurate-.*:.*"))
 *     }
 * }
 * }</pre>
 *
 * The same rule applies to any codec this module registers for the menu config: if it is reached only through
 * a service file, list that resource (and the class) under the {@code minimize} excludes so it survives.
 */
@NullMarked
package com.uxplima.uxmlib.gui.config;

import org.jspecify.annotations.NullMarked;
