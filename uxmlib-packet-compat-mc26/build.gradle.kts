plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
    alias(libs.plugins.paperweight.userdev)
}

// The 26.x half of the compat seam. It is compiled against the same dev bundle as the packet modules, which
// makes it the line the rest of the library is written against; the seam exists so the older line does not
// have to be.
dependencies {
    api(project(":uxmlib-packet-compat"))

    paperweight.paperDevBundle(libs.versions.paper.get())
}

paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}
