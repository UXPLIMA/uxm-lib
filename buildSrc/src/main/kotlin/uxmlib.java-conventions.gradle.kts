import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("java-library")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

// The compiler and the bytecode target are deliberately different versions. Paper 26.x ships Java 25
// class files, so nothing older than a Java 25 compiler can even read paper-api; the emitted bytecode
// stays at Java 21 so a single published jar keeps running on the 1.21.x line, which is still Java 21.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.toolchain.get().toInt())
        vendor = JvmVendorSpec.ADOPTIUM
    }
    // A published library ships sources and javadoc alongside the binary.
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    "compileOnly"(libs.jspecify)
    "testCompileOnly"(libs.jspecify)
    "errorprone"(libs.errorprone.core)
    "errorprone"(libs.nullaway)

    "testImplementation"(platform(libs.junit.bom))
    "testImplementation"(libs.bundles.testing)
    // Gradle 9 no longer bundles junit-platform-launcher in the test runtime; declare it explicitly.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// The library is published as one jar for two server lines, so nothing may touch API that exists on only one
// of them. `-PmcTarget=1.21` is how that stays a checked fact rather than an assumption: it rebuilds and
// retests the platform-neutral modules against the oldest supported line, and anything reaching for a 26.x-only
// symbol fails there. The modules that reach into the server cannot take part in such a run, because a dev
// bundle is pinned to one line by nature; `:uxmlib-nms-check-mc1_21` covers them instead by recompiling their
// sources against the older bundle, and the compat seam covers what genuinely differs.
if (providers.gradleProperty("mcTarget").orNull == "1.21") {
    // Paper bundles Adventure, so the line under test decides that version too. The BOM is what pins it:
    // it lists exactly the core artifacts and nothing from the separately versioned adventure-platform
    // line, which MockBukkit drags in and which must keep its own versions.
    dependencies {
        "compileOnly"(enforcedPlatform(libs.adventure.bom.legacy))
        "testImplementation"(enforcedPlatform(libs.adventure.bom.legacy))
    }
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.papermc.paper" && requested.name == "paper-api") {
                useVersion(libs.versions.legacy.paper.get())
            }
        }
        // MockBukkit ships one artifact per server line rather than one artifact with many versions.
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.mockbukkit.mockbukkit:mockbukkit-v26.2"))
                .using(
                    module("org.mockbukkit.mockbukkit:mockbukkit-v1.21:" + libs.versions.legacy.mockbukkit.get()),
                )
        }
    }
}

// Gradle resolves dependencies by variant, and paper-api 26.x publishes metadata declaring it needs a
// Java 25 runtime. Matched against `options.release = 21` that looks like an incompatibility and the
// dependency is rejected outright. The declared level constrains the bytecode this project *emits*;
// it says nothing about what the compiler can *read*, and a JDK 25 javac reads Java 25 class files
// while still emitting Java 21. Widen what these classpaths accept, and leave the target alone.
listOf(
    configurations.compileClasspath,
    configurations.runtimeClasspath,
    configurations.testCompileClasspath,
    configurations.testRuntimeClasspath,
).forEach { classpath ->
    classpath.configure {
        attributes {
            attribute(
                org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                libs.versions.java.toolchain.get().toInt(),
            )
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = libs.versions.java.release.get().toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
            "-Xlint:-processing",
            "-Xlint:-serial",
            "-Werror",
            "-parameters",
        ),
    )
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
    }
}

extensions.configure<net.ltgt.gradle.nullaway.NullAwayExtension> {
    onlyNullMarked.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.nullaway {
        // CheckSeverity is reused from the errorprone plugin — the nullaway plugin defines no enum.
        severity.set(net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    }
}

// Doclint off: the public API is documented for humans, not for the strict javadoc tool, and missing
// @param/@return on internal helpers must never fail the build of a library jar.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        palantirJavaFormat(libs.versions.palantir.fmt.get())
        removeUnusedImports()
        formatAnnotations()
        importOrder("java", "javax", "org.bukkit", "io.papermc", "net.kyori", "")
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
    kotlinGradle { ktlint("1.5.0") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        // The HTML report a failure points at lives on the CI runner and is gone when the job ends, so a
        // failing assertion has to say what it actually saw in the console log or nobody can read it.
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
