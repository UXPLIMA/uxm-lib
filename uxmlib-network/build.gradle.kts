plugins {
    id("uxm.library")
}

dependencies {
    // Plain Java on purpose: the broker runs inside a Velocity plugin and the client inside a Paper plugin,
    // so nothing here may name a server type. Redis stays soft, as in uxmlib-redis itself.
    api(project(":uxmlib-redis"))
}
