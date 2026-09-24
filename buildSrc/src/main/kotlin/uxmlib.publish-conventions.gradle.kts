plugins {
    id("maven-publish")
}

// A library module is consumed two ways (the project's "both" distribution choice): pulled as a Maven
// artifact and shaded by the consumer, or run inside the standalone uxmlib-all plugin. This convention
// publishes the binary + sources + javadoc with proper POM metadata so a Maven repo can serve it.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("uxmLib: a modern toolkit library for Paper 1.21+ plugins")
                url.set("https://github.com/UXPLIMA/uxm-lib")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("siracozmen")
                        name.set("Sirac Ozmen")
                    }
                }
                scm {
                    url.set("https://github.com/UXPLIMA/uxm-lib")
                    connection.set("scm:git:https://github.com/UXPLIMA/uxm-lib.git")
                    developerConnection.set("scm:git:git@github.com:UXPLIMA/uxm-lib.git")
                }
            }
        }
    }

    // WHERE THE ARTIFACTS GO. Until this block existed the library had 144 tags and no publish target:
    // every consumer reached it through JitPack, through mavenLocal(), or -- in uxmEssentials' CI -- by
    // cloning this repository and wiring it in with includeBuild. That last one is what broke when the
    // organisation moved to Forgejo: a job's automatic token is scoped to its own repository, so the
    // cross-repository checkout answered 403 and the build stopped. A published artifact has no such
    // problem, which is the whole reason this block is here.
    repositories {
        maven {
            name = "uxplima-nexus"
            // maven-snapshots accepts the -SNAPSHOT timestamped layout and maven-releases refuses a
            // redeploy of the same coordinate, so the suffix has to pick the repository. Publishing a
            // release build into snapshots would let it be overwritten later; the reverse is rejected
            // outright by Nexus and the failure only surfaces at the end of a release run.
            val snapshot = project.version.toString().endsWith("SNAPSHOT")
            url = uri(
                if (snapshot) "https://repo.uxplima.com/repository/maven-snapshots/"
                else "https://repo.uxplima.com/repository/maven-releases/"
            )
            // Two sources, on purpose. CI passes the credentials as environment variables, while a
            // developer publishing by hand keeps them in ~/.gradle/gradle.properties and should not have
            // to export anything. The property wins so a local override is possible on a machine where
            // the environment already carries a different account.
            credentials {
                username = providers.gradleProperty("uxplimaNexusUser").orNull
                    ?: System.getenv("UXPLIMA_NEXUS_USER")
                password = providers.gradleProperty("uxplimaNexusPass").orNull
                    ?: System.getenv("UXPLIMA_NEXUS_PASS")
            }
        }
    }
}
