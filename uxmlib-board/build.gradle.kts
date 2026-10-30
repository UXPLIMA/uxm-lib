plugins {
    id("uxm.library")
}

dependencies {
    // The SQL store runs on uxmlib-storage; the config reader and the scheduler come from common through it.
    api(project(":uxmlib-storage"))
    // The presence listener reads the login and quit events. Paper supplies the API at runtime.
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(project(":uxmlib-storage-testing"))
}
