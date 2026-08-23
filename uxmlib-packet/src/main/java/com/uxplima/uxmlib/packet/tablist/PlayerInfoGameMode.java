package com.uxplima.uxmlib.packet.tablist;

/**
 * The game-mode value carried by a player-info entry. This deliberately mirrors the four protocol-visible
 * modes without exposing Bukkit or Mojang server types through the packet port.
 */
public enum PlayerInfoGameMode {
    SURVIVAL,
    CREATIVE,
    ADVENTURE,
    SPECTATOR
}
