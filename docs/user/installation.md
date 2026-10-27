# Installation

uxmLib is published to `repo.uxplima.com` under the group `com.uxplima.uxmlib`. Reading it needs no
credentials. Every coordinate ends in a module: there is no `com.uxplima:uxmlib` artifact. Replace
`VERSION` with the latest release.

Adventure, MiniMessage and Brigadier come with Paper at runtime: uxmLib compiles against them and never
ships them.

## Gradle

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.uxplima.com/repository/maven-public/")
}

dependencies {
    implementation(platform("com.uxplima.uxmlib:uxmlib-bom:VERSION"))
    implementation("com.uxplima.uxmlib:uxmlib-gui")
    implementation("com.uxplima.uxmlib:uxmlib-item")
    implementation("com.uxplima.uxmlib:uxmlib-storage")
}
```

The BOM aligns every module to one version. `uxmlib-all` depends on every module, so one line pulls in the
whole surface:

```kotlin
dependencies {
    implementation("com.uxplima.uxmlib:uxmlib-all:VERSION")
}
```

A UXPLIMA plugin built with `uxm-gradle` takes these from the `uxm` catalog instead of naming versions.

## Maven

```xml
<repositories>
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
  <repository>
    <id>uxplima</id>
    <url>https://repo.uxplima.com/repository/maven-public/</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.uxplima.uxmlib</groupId>
  <artifactId>uxmlib-gui</artifactId>
  <version>VERSION</version>
</dependency>
```

## Standalone plugin jar

Instead of shading, put the standalone jar in the server's `plugins/` folder and depend on it:

```yaml
# paper-plugin.yml
depend:
  - uxmlib
```

The standalone jar registers only the few listeners driven entirely by item persistent data; the rest of
the library is an API. It is published beside the aggregate under the `standalone` classifier, because it
bundles Configurate, HikariCP and Caffeine under relocated package names, which a consumer must not compile
against:

```kotlin
// only to get the file itself, for example to copy it into a server image
dependencies {
    runtimeOnly("com.uxplima.uxmlib:uxmlib-all:VERSION:standalone")
}
```

## Shading and relocation

When you shade modules into your own jar, relocate `com.uxplima.uxmlib` so it cannot clash with another
plugin that bundles uxmLib. Two relocated copies share no class, so anything two plugins hand each other
goes through a JDK or Bukkit type, or through a plain key on a player's data:

```kotlin
tasks.shadowJar {
    relocate("com.uxplima.uxmlib", "com.yourplugin.libs.uxmlib")
}
```

The config and storage layers find their codecs through the JDK `ServiceLoader`. With Shadow's
`minimize()`, keep the service files and their classes, or config parsing fails at runtime with a missing
serializer:

```kotlin
tasks.shadowJar {
    minimize {
        exclude("META-INF/services/**")
        exclude(dependency("org.spongepowered:configurate-.*:.*"))
    }
}
```
