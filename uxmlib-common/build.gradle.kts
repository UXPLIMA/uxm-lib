plugins {
    id("uxm.library")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    api(libs.configurate.hocon)

    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}
