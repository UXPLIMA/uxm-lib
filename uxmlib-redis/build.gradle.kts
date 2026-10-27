plugins {
    id("uxm.library")
}

dependencies {
    // Lettuce backs the byte[] Redis pub/sub: a soft-dependency: compileOnly here, constructed only when a
    // Redis URI is configured, so a consumer that never touches Redis ships nothing extra; one that does adds
    // io.lettuce:lettuce-core. Deliberately NOT depending on uxmlib-storage (which drags HikariCP/Caffeine/
    // sqlite-jdbc) so a pure cross-server-messaging consumer stays lean.
    compileOnly(libs.lettuce.core)
}
