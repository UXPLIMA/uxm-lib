package com.uxplima.uxmlib.packet;

import java.util.Objects;
import java.util.UUID;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.jspecify.annotations.Nullable;

/**
 * Builds the {@link GameProfile} a fake player is rendered from: an NPC body or a tab-list entry. authlib's
 * two-argument {@code GameProfile(id, name)} constructor seats an immutable property map, so attaching the skin
 * afterwards through {@code profile.properties().put(...)} throws {@link UnsupportedOperationException} on the
 * server's authlib. This helper builds a mutable {@link PropertyMap} first and hands it to the three-argument
 * constructor (the construction order that actually lets a profile carry a {@code textures} property), so the
 * two packet renderers no longer each risk that trap.
 */
public final class GameProfiles {

    private GameProfiles() {}

    /**
     * A profile carrying a single {@code textures} property. {@code signature} may be {@code null} for an
     * unsigned (synthetic) skin, which is fine for an NPC profile the client never re-verifies against Mojang.
     */
    public static GameProfile withTextures(UUID id, String name, String textureValue, @Nullable String signature) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(textureValue, "textureValue");
        Multimap<String, Property> properties = LinkedHashMultimap.create();
        properties.put("textures", new Property("textures", textureValue, signature));
        return new GameProfile(id, name, new PropertyMap(properties));
    }

    /** A profile with no properties: a tab-list entry or NPC that carries no skin. */
    public static GameProfile plain(UUID id, String name) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        return new GameProfile(id, name);
    }
}
