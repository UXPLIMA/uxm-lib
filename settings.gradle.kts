pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "uxmlib"

include(
    ":uxmlib-bom",
    ":uxmlib-common",
    ":uxmlib-item",
    ":uxmlib-command",
    ":uxmlib-gui",
    ":uxmlib-storage",
    ":uxmlib-redis",
    ":uxmlib-integration",
    ":uxmlib-hud",
    ":uxmlib-update",
    ":uxmlib-condition",
    ":uxmlib-npc",
    ":uxmlib-packet",
    ":uxmlib-packet-compat",
    ":uxmlib-packet-compat-mc1_21",
    ":uxmlib-packet-compat-mc26",
    ":uxmlib-nametags",
    ":uxmlib-nms-check-mc1_21",
    ":uxmlib-all",
)
