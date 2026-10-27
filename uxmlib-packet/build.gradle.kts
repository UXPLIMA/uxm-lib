plugins {
    id("uxm.library")
    alias(libs.plugins.paperweight.userdev)
}

// The shared, MIT-clean packet foundation: the generic Mojang-mapped machinery (Adventure -> vanilla component
// conversion, bundle building, the stream-codec buffer trick, the guarded accessor reflection, the entity-id
// allocator) that more than one packet feature needs. The nametag renderer was the first consumer; the tablist
// renderer is the second. NMS is quarantined to the small helper classes here, mirroring uxmlib-nametags. The
// Netty plumbing (channel resolve, packet send) comes from uxmlib-pipeline.
dependencies {
    api(project(":uxmlib-pipeline"))

    // The Mojang-mapped dev bundle supplies the Paper API *and* the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the packet construction needs; it replaces the plain paper-api compile
    // dependency for the main source set. Paper's runtime remapper maps the Mojang-mapped classes back
    // to the server mappings at load when the consumer ships the namespace manifest attribute.
    paperweight.devBundle(libs.devbundle.modern)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    // Paper bundles Netty at runtime but does not export it through its POM; the channel/pipeline types are
    // a compileOnly dependency pinned to the version the server ships. Infra dep; the consumer never shades it.
    compileOnly(libs.netty.transport)
}

// The server internals are compileOnly: the live server provides net.minecraft at runtime.
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}

// This module and uxmlib-nametags set up the same dev bundle, and with parallel builds the two setup tasks
// would race for its lock. Ordering them costs one wait on a cold cache.
tasks.named("paperweightUserdevSetup") {
    mustRunAfter(":uxmlib-nametags:paperweightUserdevSetup")
}
