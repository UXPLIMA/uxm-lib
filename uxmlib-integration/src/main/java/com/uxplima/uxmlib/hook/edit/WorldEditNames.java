package com.uxplima.uxmlib.hook.edit;

/**
 * The plugin names the edit seam asks about, in a class that names no WorldEdit type.
 *
 * <p>{@link EditGuard#forServer()} reads them before it touches {@link WorldEditGuard}, and a constant read from
 * {@code WorldEditGuard} itself would have linked that class to read it.
 */
final class WorldEditNames {

    static final String WORLD_EDIT = "WorldEdit";

    static final String FAST_ASYNC = "FastAsyncWorldEdit";

    private WorldEditNames() {}
}
