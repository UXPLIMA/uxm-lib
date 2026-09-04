package com.uxplima.uxmlib.text.language;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

/**
 * How a {@link LanguageService} is found and how one is offered, through Bukkit's own service manager.
 *
 * <p>A plugin that reads a language passes {@link #registered} as the supplier of its
 * {@link LanguageResolver}, so a provider that enables after it is still found, and a provider that is
 * disabled stops being used. A plugin that provides one calls {@link #register} when it enables.
 *
 * <p>This is a hook and never a requirement. A server with no provider loses one convenience, a language
 * chosen once for every plugin, and loses nothing else.
 */
public final class LanguageServices {

    private LanguageServices() {}

    /** The registered provider, or empty when the server runs none. Cheap enough to call per message. */
    public static Optional<LanguageService> registered() {
        RegisteredServiceProvider<LanguageService> registration =
                Bukkit.getServicesManager().getRegistration(LanguageService.class);
        return registration == null ? Optional.empty() : Optional.of(registration.getProvider());
    }

    /** Offer {@code service} to every plugin on the server. */
    public static void register(Plugin plugin, LanguageService service) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(service, "service");
        Bukkit.getServicesManager().register(LanguageService.class, service, plugin, ServicePriority.Normal);
    }
}
