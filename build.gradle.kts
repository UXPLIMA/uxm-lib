// No root `plugins {}` block. Spotless / Error Prone / NullAway are pulled onto every
// subproject's buildscript classpath via the buildSrc convention plugin; redeclaring them here
// with `apply false` makes Gradle 9.x fail with "plugin already on classpath with an unknown version".

allprojects {
    group = "com.uxplima.uxmlib"
    version = project.findProperty("projectVersion")?.toString() ?: "0.34.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
        maven("https://jitpack.io")                       // Vault API
        maven("https://repo.codemc.io/repository/creatorfromhell/") // VaultUnlocked API
        maven("https://maven.enginehub.org/repo/")        // WorldGuard / WorldEdit
        maven("https://repo.glaremasters.me/repository/towny/") // Towny
    }
}
