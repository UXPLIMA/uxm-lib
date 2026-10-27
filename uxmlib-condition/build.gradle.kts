plugins {
    id("uxm.library")
}

dependencies {
    api(project(":uxmlib-common")) // Text seam for rendering failure messages
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    // The two wallet backends that name a type of another plugin. Both are soft: every typed line sits in a
    // method reached only past a plugin-present guard, so a server without them loads none of their code.
    // The other economies (Vault, VaultUnlocked, PlayerPoints, EcoBits) are descriptions rather than
    // coordinates, so they add nothing to this list and nothing to the build.
    compileOnly(libs.treasury.api)
    compileOnly(libs.placeholderapi)
}
