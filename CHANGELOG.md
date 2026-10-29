# Changelog

## [0.1.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.0.2...uxm-lib-v0.1.0) (2026-10-29)


### Features

* **condition:** add the currency layer ([#50](https://github.com/UXPLIMA/uxm-lib/issues/50)) ([f393ba0](https://github.com/UXPLIMA/uxm-lib/commit/f393ba05206f2abf749c6546aa619cfdda463ec1)), closes [#49](https://github.com/UXPLIMA/uxm-lib/issues/49)
* **network:** add a bus between the servers of a network ([#55](https://github.com/UXPLIMA/uxm-lib/issues/55)) ([1dc4190](https://github.com/UXPLIMA/uxm-lib/commit/1dc4190e0fcbf64831597eb9b598a2db60fc0fb8)), closes [#54](https://github.com/UXPLIMA/uxm-lib/issues/54)


### Bug Fixes

* **common:** make handles on the same scheduled task equal ([#45](https://github.com/UXPLIMA/uxm-lib/issues/45)) ([e29744b](https://github.com/UXPLIMA/uxm-lib/commit/e29744b84328a6c8639edd82bfe49f65d0f22e91)), closes [#44](https://github.com/UXPLIMA/uxm-lib/issues/44)
* **condition:** never report a money move as undone while it can still run ([#53](https://github.com/UXPLIMA/uxm-lib/issues/53)) ([147f890](https://github.com/UXPLIMA/uxm-lib/commit/147f890ef5ec39f8c1cfac03d20a6c664a87e809)), closes [#52](https://github.com/UXPLIMA/uxm-lib/issues/52)
* **nametags:** show nametags only to clients that track the target ([#48](https://github.com/UXPLIMA/uxm-lib/issues/48)) ([99dea2b](https://github.com/UXPLIMA/uxm-lib/commit/99dea2b5c92d2fddaca962f81a71bf74a93fb8f8)), closes [#47](https://github.com/UXPLIMA/uxm-lib/issues/47)

## [0.0.2](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.0.1...uxm-lib-v0.0.2) (2026-10-27)

### Fixed

- `uxmlib-all` is complete again: 0.0.1 lacks its standalone, sources and javadoc jars because their upload was cut off. The code is the same as 0.0.1; use 0.0.2.

## 0.0.1 (2026-10-27)

### Changed

- Versions restart at 0.0.1. Gradle and Maven order 0.0.1 below the old 0.x releases, so a plugin moving to this line names the version exactly instead of taking the newest.
- The build comes from `uxm-gradle`: every module is published from `repo.uxplima.com` with sources and javadoc jars, and `uxmlib-bom` aligns them.
- The modules emit Java 21 bytecode and compile against Paper 26.3.

### Removed

- The old test suite and the JitPack build. Nothing in the published API changed.
