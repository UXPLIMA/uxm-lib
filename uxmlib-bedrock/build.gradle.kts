plugins {
    id("uxm.library")
}

dependencies {
    compileOnly(libs.paper.api)

    // Floodgate is a soft-depend, under the same rule as PlaceholderAPI and Vault: compileOnly, never shaded,
    // and named only past a plugin-present guard. Cumulus, the form library, arrives with it. A Java-only
    // server therefore loads neither, because the two classes that name them are constructed only inside the
    // enabled branch of the two factories.
    compileOnly(libs.floodgate.api)
}
