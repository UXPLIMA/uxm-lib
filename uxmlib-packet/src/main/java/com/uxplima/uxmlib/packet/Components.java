package com.uxplima.uxmlib.packet;

import java.util.Objects;

import io.papermc.paper.adventure.PaperAdventure;

import org.jspecify.annotations.Nullable;

/**
 * Converts an Adventure {@link net.kyori.adventure.text.Component} into the vanilla
 * {@link net.minecraft.network.chat.Component} that data-watcher values and packet fields expect. The
 * conversion itself is Paper's own {@link PaperAdventure} bridge; this class exists only to keep that single
 * {@code net.minecraft} touch in the quarantined packet module rather than scattered across renderers.
 */
public final class Components {

    private Components() {}

    /** Convert an Adventure component to the server's vanilla component. */
    public static net.minecraft.network.chat.Component asVanilla(net.kyori.adventure.text.Component component) {
        Objects.requireNonNull(component, "component");
        return PaperAdventure.asVanilla(component);
    }

    /** Convert a nullable vanilla component to its nullable Adventure counterpart. */
    public static net.kyori.adventure.text.@Nullable Component asAdventure(
            net.minecraft.network.chat.@Nullable Component component) {
        return component == null ? null : PaperAdventure.asAdventure(component);
    }

    /** Convert a nullable Adventure component to its nullable vanilla counterpart. */
    public static net.minecraft.network.chat.@Nullable Component asVanillaNullable(
            net.kyori.adventure.text.@Nullable Component component) {
        return component == null ? null : PaperAdventure.asVanilla(component);
    }
}
