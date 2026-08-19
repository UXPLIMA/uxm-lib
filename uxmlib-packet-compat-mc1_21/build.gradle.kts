plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
    alias(libs.plugins.paperweight.userdev)
}

// The 1.21.x half of the compat seam, compiled against that line's own dev bundle. Nothing else in the
// library is built against 1.21.x, so this artifact is where the old spelling of a moved internal stays
// compiler-verified instead of being reconstructed by reflection at runtime.
dependencies {
    api(project(":uxmlib-packet-compat"))

    paperweight.paperDevBundle(libs.versions.legacy.paper.get())
}

paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}
