plugins {
    id("uxmlib.java-conventions")
    alias(libs.plugins.paperweight.userdev)
}

// Nothing here is published, shipped or written by hand. The packet modules are compiled against the newest
// supported server and are expected to keep loading on the oldest one; this module is what turns that
// expectation into something the build checks. It compiles their sources a second time against the oldest
// line's dev bundle, so a server internal that quietly stopped existing there fails the build here instead of
// on someone's server. What genuinely differs between the lines is behind the compat seam and is not in the
// shared sources at all, which is what keeps this compile honest rather than a formality.
sourceSets {
    main {
        java.setSrcDirs(
            listOf(
                "../../uxmlib-packet/src/main/java",
                "../../uxmlib-nametags/src/main/java",
            ),
        )
        resources.setSrcDirs(emptyList<String>())
    }
}

// The API on this classpath has to be the one inside the 1.21.x dev bundle. The library's own modules are
// built against the newer Paper and would otherwise drag it back in behind the bundle.
fun ModuleDependency.withoutPaperApi() {
    exclude(group = "io.papermc.paper", module = "paper-api")
}

dependencies {
    paperweight.devBundle(libs.devbundle.legacy)

    compileOnly(project(":uxmlib-common")) { withoutPaperApi() }
    compileOnly(project(":uxmlib-npc")) { withoutPaperApi() }
    compileOnly(project(":uxmlib-packet-compat")) { withoutPaperApi() }
    compileOnly(project(":uxmlib-packet-compat-mc1_21")) { withoutPaperApi() }
    compileOnly(project(":uxmlib-packet-compat-mc26")) { withoutPaperApi() }

    // Paper bundles Adventure, so the line under test decides that version too, and on the oldest line it is
    // older than what the rest of the build compiles against. Pinning it here means this run also proves the
    // packet sources stay inside the Adventure API that line actually ships.
    compileOnly(enforcedPlatform(libs.adventure.bom.legacy))
    compileOnly(libs.bundles.adventure)
    compileOnly(libs.netty.transport)
}

paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}

// The java sources belong to the packet modules and are formatted there. Formatting them from here as well
// would have two projects writing the same files; this module's own build script stays covered.
tasks.matching { it.name.startsWith("spotlessJava") }.configureEach { enabled = false }

// A second copy of the same javadoc is pure build time for a module that publishes nothing.
tasks.named<Javadoc>("javadoc") { enabled = false }
