plugins {
    id("uxmlib.java-conventions")
    id("uxmlib.publish-conventions")
}

dependencies {
    api(project(":uxmlib-common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)

    // MockBukkit drives the real Paper API in tests; the production set declares Paper/Adventure
    // compileOnly, so the test set needs them on its runtime classpath.
    testImplementation(libs.mockbukkit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
}

// The corpus: structure files real tools wrote, which are third party work or too large to keep in the
// repository. They are read only when asked, ./gradlew :uxmlib-schematic:corpusTest -Pcorpus=<folder>,
// and a run without the folder fails rather than passing on nothing.
tasks.test {
    useJUnitPlatform { excludeTags("corpus") }
}

tasks.register<Test>("corpusTest") {
    description = "Reads the structure files of a corpus folder given as -Pcorpus=<folder>."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("corpus") }
    val folder = providers.gradleProperty("corpus")
    inputs.property("corpus", folder.orElse(""))
    doFirst {
        require(folder.isPresent) { "corpusTest reads the corpus folder given as -Pcorpus=<folder>" }
        systemProperty("uxmlib.schematic.corpus", folder.get())
    }
}
