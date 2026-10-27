# Changelog

## [0.0.2](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.0.1...uxm-lib-v0.0.2) (2026-10-27)


### Continuous Integration

* **release:** check that the standalone jar reached the repository ([#40](https://github.com/UXPLIMA/uxm-lib/issues/40)) ([70f2b45](https://github.com/UXPLIMA/uxm-lib/commit/70f2b45cfdd9f426150aa472e37b4c20617710c7))

## 0.0.1 (2026-10-27)

### Changed

- Versions restart at 0.0.1. Gradle and Maven order 0.0.1 below the old 0.x releases, so a plugin moving to this line names the version exactly instead of taking the newest.
- The build comes from `uxm-gradle`: every module is published from `repo.uxplima.com` with sources and javadoc jars, and `uxmlib-bom` aligns them.
- The modules emit Java 21 bytecode and compile against Paper 26.3.

### Removed

- The old test suite and the JitPack build. Nothing in the published API changed.
