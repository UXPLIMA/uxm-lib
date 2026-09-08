plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    // Lettuce backs the byte[] Redis pub/sub: a soft-dependency: compileOnly here, constructed only when a
    // Redis URI is configured, so a consumer that never touches Redis ships nothing extra; one that does adds
    // io.lettuce:lettuce-core. Deliberately NOT depending on uxmlib-storage (which drags HikariCP/Caffeine/
    // sqlite-jdbc) so a pure cross-server-messaging consumer stays lean.
    compileOnly(libs.lettuce.core)

    // Lettuce on the test runtime so the bus links.
    testImplementation(libs.lettuce.core)
    testImplementation(libs.bundles.testing)
}

// The live round trip needs a Redis this machine may not have, so it lives in its own source set and `check`
// never runs it. It sat in src/test and opened with an assumeTrue that aborted when no broker answered: JUnit
// records an abort as a skip, so `./gradlew build` on a host without Redis went green having proved nothing,
// and nothing said so. This machine happens to run one, which is exactly why the hole was invisible here.
// Run it with `./gradlew :uxmlib-redis:integrationTest`, with UXMLIB_TEST_REDIS_URI or a Redis on :6379.
val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestCompileOnly"].extendsFrom(configurations["testCompileOnly"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "The Redis round trip, against a real broker. Not part of check: it needs one to exist."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
}
