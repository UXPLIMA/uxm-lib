plugins {
    `java-platform`
    `maven-publish`
}

// A BOM so a consumer can align every uxmlib-* artifact to one version with a single platform import.
dependencies {
    constraints {
        api(project(":uxmlib-common"))
        api(project(":uxmlib-item"))
        api(project(":uxmlib-command"))
        api(project(":uxmlib-gui"))
        api(project(":uxmlib-bedrock"))
        api(project(":uxmlib-storage"))
        api(project(":uxmlib-storage-testing"))
        api(project(":uxmlib-redis"))
        api(project(":uxmlib-network"))
        api(project(":uxmlib-integration"))
        api(project(":uxmlib-hud"))
        api(project(":uxmlib-update"))
        api(project(":uxmlib-condition"))
        api(project(":uxmlib-pipeline"))
        api(project(":uxmlib-packet"))
        api(project(":uxmlib-menu"))
        api(project(":uxmlib-nametags"))
        api(project(":uxmlib-schematic"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            pom {
                name.set("uxmlib-bom")
                description.set("Bill of materials aligning all uxmlib-* module versions")
                url.set("https://github.com/UXPLIMA/uxm-lib")
                licenses { license { name.set("MIT") } }
            }
        }
    }
    repositories {
        maven {
            name = "uxplima"
            val snapshot = version.toString().endsWith("-SNAPSHOT")
            url = uri("https://repo.uxplima.com/repository/maven-${if (snapshot) "snapshots" else "releases"}/")
            credentials {
                username = providers.gradleProperty("uxplimaNexusUser").orElse(providers.environmentVariable("UXPLIMA_NEXUS_USER")).orNull
                password = providers.gradleProperty("uxplimaNexusPass").orElse(providers.environmentVariable("UXPLIMA_NEXUS_PASS")).orNull
            }
        }
    }
}
