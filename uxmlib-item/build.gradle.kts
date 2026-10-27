plugins {
    id("uxm.library")
}

dependencies {
    api(project(":uxmlib-common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
}
