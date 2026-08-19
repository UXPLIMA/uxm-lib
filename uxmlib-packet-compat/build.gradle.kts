plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
    alias(libs.plugins.paperweight.userdev)
}

// The seam for the few server internals that are not the same on every Minecraft line uxmLib supports.
// Everything else the packet modules touch exists unchanged on both lines and is written against it
// directly; only what genuinely moved lives behind this interface, with one implementation per line.
//
// The interface itself is compiled against the newest dev bundle, which is safe because its signatures name
// only types that exist on every supported line. If a future line ever renames one of those, the method has
// to change shape here first, which is exactly where such a break should surface.
dependencies {
    paperweight.devBundle(libs.devbundle.modern)
}

// The dev bundle stays on compileOnly: the server supplies these classes at runtime, and pulling the whole
// mojang-mapped server onto the test runtime would run its static initialisers.
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}
