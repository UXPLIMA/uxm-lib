# Development

The build comes from `uxm-gradle`: every module applies `uxm.library`, which sets the toolchain, the
Java 21 release, Spotless, Error Prone, NullAway and publishing. Versions of the platform and the shared
tools come from the `uxm` catalog; the libraries only uxmLib uses are in `gradle/libs.versions.toml`.

## Build

```sh
./gradlew build                   # compile, format check, static analysis, tests
./gradlew spotlessApply           # format before building
./gradlew :uxmlib-all:shadowJar   # build/libs/uxmlib-all-<version>-standalone.jar
```

## Try a change in a plugin

A change reaches a plugin only through a published version. To build a plugin against local work:

```sh
./gradlew publishToMavenLocal
```

This installs `projectVersion` from `gradle.properties`, a `-SNAPSHOT`. The plugin adds `mavenLocal()` and
asks for that version. Do not commit either change in the plugin.

## Release

release-please keeps a release pull request open on `main`. Before merging it, rewrite its `CHANGELOG.md`
entry for plugin authors: Added, Changed, Fixed, Removed. Merging it tags the release, and the release
workflow publishes every module to `maven-releases`, which refuses to overwrite a version.

A manual publish is the recovery path when the workflow cannot run:

```sh
./gradlew -PprojectVersion=0.0.1 publish
```

Credentials come from `uxplimaNexusUser` and `uxplimaNexusPass` in `~/.gradle/gradle.properties`, or from
`UXPLIMA_NEXUS_USER` and `UXPLIMA_NEXUS_PASS` in the environment. A version ending in `-SNAPSHOT` goes to
`maven-snapshots`.
