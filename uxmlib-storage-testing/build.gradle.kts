plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

// A test dependency for a consumer, never shipped in a plugin: it starts database servers in containers so a
// storage test runs on every backend a configuration can name, not on SQLite alone.
dependencies {
    api(project(":uxmlib-storage"))
    api(platform(libs.junit.bom))
    api(libs.junit.params)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers.mysql)
    implementation(libs.testcontainers.mariadb)
    implementation(libs.testcontainers.postgresql)
    // The drivers the backends are reached through. A server supplies MySQL's and the plugin's loader the
    // other two, so a consumer's tests would otherwise have none of them.
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.mariadb.jdbc)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.slf4j.api)
}
