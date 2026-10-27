pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.uxplima.com/repository/maven-public/")
    }
    plugins {
        id("uxm.library") version "0.0.2"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo.uxplima.com/repository/maven-public/")
    }
    versionCatalogs {
        // The shared versions the conventions build with. `libs` (gradle/libs.versions.toml) keeps
        // the dependencies only this library uses.
        create("uxm") { from("com.uxplima.gradle:uxm-catalog:0.0.2") }
    }
}

rootProject.name = "uxmlib"

include(
    ":uxmlib-bom",
    ":uxmlib-common",
    ":uxmlib-item",
    ":uxmlib-command",
    ":uxmlib-gui",
    ":uxmlib-bedrock",
    ":uxmlib-storage",
    ":uxmlib-storage-testing",
    ":uxmlib-redis",
    ":uxmlib-integration",
    ":uxmlib-hud",
    ":uxmlib-update",
    ":uxmlib-condition",
    ":uxmlib-pipeline",
    ":uxmlib-packet",
    ":uxmlib-menu",
    ":uxmlib-nametags",
    ":uxmlib-schematic",
    ":uxmlib-all",
)
