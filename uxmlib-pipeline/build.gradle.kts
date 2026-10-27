plugins {
    id("uxm.library")
}

// EXPERIMENTAL: a from-scratch, MIT-clean Netty pipeline (no PacketEvents, which is GPL). Channel resolve,
// inject and eject, self-healing reorder, and a listener seam. It builds no packet and knows no entity: the
// features that do sit above it in uxmlib-packet and uxmlib-nametags.
//
// It compiles against plain paper-api on purpose. Everything above it applies paperweight and publishes
// Mojang mapped, which asks a consumer for the namespace manifest attribute; this module asks for nothing.
// That is the boundary the module exists to hold. See the package-info for the status note.
dependencies {
    api(project(":uxmlib-common")) // Scheduler abstraction (Folia-ready inject/reorder choreography)
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    // Paper bundles Netty at runtime but paper-api's POM does not export it, so the channel/pipeline types are
    // a compileOnly dependency pinned to the version the server ships. Infra dep; the consumer never shades it.
    compileOnly(libs.netty.transport)
}
