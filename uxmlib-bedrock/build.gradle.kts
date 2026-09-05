plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    compileOnly(libs.paper.api)

    // Floodgate is a soft-depend, under the same rule as PlaceholderAPI and Vault: compileOnly, never shaded,
    // and named only past a plugin-present guard. Cumulus, the form library, arrives with it. A Java-only
    // server therefore loads neither, because the two classes that name them are constructed only inside the
    // enabled branch of the two factories.
    compileOnly(libs.floodgate.api)

    // The tests drive the two Java-only defaults and the factory selection, so they need a server to probe for
    // plugins. Neither SDK is on the test runtime, on purpose: that is what proves the guarded classes are
    // never reached without their plugin.
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
}
