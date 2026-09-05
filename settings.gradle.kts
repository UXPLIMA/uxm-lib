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
    ":uxmlib-bedrock",
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

// Everything pinned to one server line sits under platform/ instead of at the root, which keeps the top of
// the repository a flat list of the modules a consumer actually names. Only the directories move: the Gradle
// paths above, and with them the published artifact ids, are unchanged.
listOf(
    "uxmlib-packet-compat",
    "uxmlib-packet-compat-mc1_21",
    "uxmlib-packet-compat-mc26",
    "uxmlib-nms-check-mc1_21",
).forEach { name ->
    project(":$name").projectDir = file("platform/$name")
}
