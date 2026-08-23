package com.uxplima.uxmlib.packet.tablist;

/** The fields a player-info update packet tells the client to consume. */
public enum PlayerInfoAction {
    ADD_PLAYER,
    INITIALIZE_CHAT,
    UPDATE_GAME_MODE,
    UPDATE_LISTED,
    UPDATE_LATENCY,
    UPDATE_DISPLAY_NAME,
    UPDATE_LIST_ORDER,
    UPDATE_HAT
}
