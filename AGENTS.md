# uxmLib

The shared library of UXPLIMA plugins: one Gradle module per concern under `com.uxplima.uxmlib`, published
to `repo.uxplima.com`. The repository is public and MIT. A change reaches a plugin only through a released
version.

## Commands

```sh
./gradlew build                       # compile, format check, Error Prone, NullAway, tests
./gradlew spotlessApply               # format
./gradlew :uxmlib-gui:build           # one module
./gradlew :uxmlib-all:shadowJar       # the standalone server jar
./gradlew publishToMavenLocal         # try a change in a plugin before releasing it
```

## Layout

- `uxmlib-<concern>/`: one module each; [docs/internals/overview.md](docs/internals/overview.md) has the graph.
- `uxmlib-bom/`: the bill of materials. `uxmlib-all/`: the aggregate and the standalone jar.
- `gradle/libs.versions.toml`: libraries only uxmLib uses. Platform and tool versions come from the `uxm`
  catalog of `uxm-gradle`.
- `tools/glyph-widths/`: measures the default font's glyph advances from the client jar.

## Ways to hurt yourself

- **Killing by pattern.** Never kill `java` or Gradle daemons by name; use `./gradlew --stop` or a PID you
  started.
- **Upward dependencies.** `uxmlib-common` depends on no module, and nothing may form a cycle. A new
  dependency between modules goes into the graph in the overview in the same pull request.
- **Hard soft-dependencies.** An integration stays `compileOnly` and is reached only past a check that its
  plugin is present. A class that names a soft dependency in a signature loaded at startup breaks every
  server without that plugin.
- **Server internals outside the quarantine.** Only `uxmlib-packet` and `uxmlib-nametags` touch
  `net.minecraft`, in named classes behind plain interfaces.
- **The main thread.** File and database IO never runs on a server thread. Scheduling goes through
  `Scheduler`, never `BukkitScheduler`, so Folia keeps working.
- **Breaking every plugin.** A removed or renamed public type, method or config key is a breaking change:
  `!` in the title and a `BREAKING CHANGE:` footer.

## Invariants

- Every package is `@NullMarked`.
- All text is Adventure built from MiniMessage. The library names no colour and no wording a player
  sees; a consumer supplies both.
- Constructor injection and no static mutable state. The only `JavaPlugin` is the shell in `uxmlib-all`.
- Nothing is copied from GPL, AGPL or proprietary sources.
- `docs/internals/uxm-conventions.md`, `.editorconfig` and `.gitattributes` are synced from `UXPLIMA/.github`.

## Testing

There are no tests yet. New tests follow the repository standard: they prove behavior a plugin relies on,
through the public API, with no assertion on structure. Storage behavior is tested on every backend with
`@EveryBackend` from `uxmlib-storage-testing`. In-game behavior is checked with Craftwire on a development
server.

## Pull requests

Titles are conventional commits with a scope: a module name without the `uxmlib-` prefix (`gui`, `menu`,
`storage`, ...), or `bom`, `all`, `build`, `ci`, `docs`, `release`. Before merging a release pull request,
rewrite its `CHANGELOG.md` entry for plugin authors: Added, Changed, Fixed, Removed.
