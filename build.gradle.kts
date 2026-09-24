// No root `plugins {}` block. Spotless / Error Prone / NullAway are pulled onto every
// subproject's buildscript classpath via the buildSrc convention plugin; redeclaring them here
// with `apply false` makes Gradle 9.x fail with "plugin already on classpath with an unknown version".

allprojects {
    // The published coordinates, and the group every plugin names this library by. It is a plain
    // default rather than a constant because -PprojectGroup is what lets a developer install the
    // library locally under some other group, to test a consumer against an unreleased change
    // without editing that consumer's build file. -PprojectVersion is how the release workflow
    // passes the tag; the fallback is only ever the local snapshot.
    group = project.findProperty("projectGroup")?.toString() ?: "com.uxplima.uxmlib"
    version = project.findProperty("projectVersion")?.toString() ?: "0.46.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
        maven("https://jitpack.io")                       // Vault API
        maven("https://repo.codemc.io/repository/creatorfromhell/") // VaultUnlocked API
        // Treasury, and only Treasury. It is declared exclusively so that no other repository is asked for
        // it: JitPack sits above this line, JitPack answers 429 while it decides whether to build a tag,
        // and a build of this library must never wait on that. me.lokka30 is not a JitPack coordinate, so
        // pinning the group here means JitPack is never asked about it at all and a cold build cannot
        // stall on it.
        exclusiveContent {
            forRepository { maven("https://repo.codemc.org/repository/maven-public/") }
            filter { includeGroup("me.lokka30") }
        }
        maven("https://maven.enginehub.org/repo/")        // WorldGuard / WorldEdit
        maven("https://repo.glaremasters.me/repository/towny/") // Towny
        maven("https://repo.opencollab.dev/main/")        // Floodgate and Cumulus
    }
}
