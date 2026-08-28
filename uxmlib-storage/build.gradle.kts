plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    // Hikari 6.2.1 still declares slf4j-api 1.7.36. Every server this runs on already has slf4j 2.x on the
    // classpath, so exporting the old API only gives a consumer who shades us a second, binding-less copy
    // next to the server's -- and a second copy with no binding behind it makes logging go quiet instead of
    // failing, which is the worst way for it to break. The server's copy is the one Hikari logs through.
    api(libs.hikari) { exclude(group = "org.slf4j", module = "slf4j-api") }
    api(libs.caffeine)

    // The Component column codec in SqlType serializes through Adventure + MiniMessage. Paper bundles both
    // at runtime (as it does for the rest of the toolkit), so they stay compileOnly here, never shaded.
    compileOnly(libs.bundles.adventure)

    // The SQLite driver is the default backend, so it ships as an api dependency. MariaDB/MySQL and
    // PostgreSQL are network options a consumer opts into; declared compileOnly here and added by the
    // consumer when they select that backend.
    api(libs.sqlite.jdbc)
    compileOnly(libs.mariadb.jdbc)
    compileOnly(libs.postgresql)
    // H2 (pure-Java embedded) is an opt-in backend like the network drivers: compileOnly here, consumer adds it.
    compileOnly(libs.h2)

    // Lettuce backs the optional RedisDataSynchronizer (cross-server pub/sub). It is a soft-dependency:
    // compileOnly here, constructed only when a Redis URI is configured, so a consumer that never touches
    // Redis ships nothing extra. On the test runtime so the adapter compiles and links against the real API.
    compileOnly(libs.lettuce.core)

    // Storage is pure infra (no Paper). Tests run a real in-memory SQLite, so they are plain JUnit. The
    // SqlType Component codec needs Adventure + MiniMessage on the test runtime since they are compileOnly.
    // Hikari logs through slf4j at runtime, so the tests that open a real pool need an API on the classpath.
    // The server provides it in production; here it is a test dependency like any other.
    testRuntimeOnly(libs.slf4j.api)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.bundles.adventure)
    // The H2 dialect round-trip test runs against a real in-memory H2, so the driver is on the test runtime.
    testImplementation(libs.h2)
    // Lettuce on the test runtime so RedisDataSynchronizer links and a smoke/integration test can construct it.
    testImplementation(libs.lettuce.core)
}
