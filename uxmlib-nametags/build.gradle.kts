plugins {
    id("uxm.library")
    alias(libs.plugins.paperweight.userdev)
}

// A from-scratch, MIT-clean per-viewer nametag renderer. The packet objects are built against the
// Mojang-mapped dev bundle and quarantined to a single NMS class; everything else stays pure. The Netty
// plumbing (channel resolve, packet send) is reused from uxmlib-pipeline, so this module depends on it.
dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-pipeline"))
    api(project(":uxmlib-packet"))

    // The Mojang-mapped dev bundle supplies the Paper API *and* the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the packet construction needs; it replaces the plain paper-api compile
    // dependency for the main source set. Paper's runtime remapper maps the Mojang-mapped classes back
    // to the server mappings at load when the consumer ships the namespace manifest attribute.
    paperweight.devBundle(libs.devbundle.modern)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    // Paper bundles Netty at runtime but does not export it through its POM; the channel/pipeline types are
    // a compileOnly dependency pinned to the version the server ships. Infra dep; the consumer never shades it.
    compileOnly(libs.netty.transport)

    // The tests drive the renderer through a fake packet port, so they need only the Paper API, not the server.
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}

// The server internals are compileOnly: the live server provides net.minecraft at runtime.
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}
