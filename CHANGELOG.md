# Changelog

## [0.2.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.1.0...uxm-lib-v0.2.0) (2026-10-29)


### ⚠ BREAKING CHANGES

* **common:** move the thread-hop helpers next to Scheduler ([#65](https://github.com/UXPLIMA/uxm-lib/issues/65))
* **condition:** build the currencies on the wallets ([#63](https://github.com/UXPLIMA/uxm-lib/issues/63))

### Bug Fixes

* **condition:** take from a wallet one at a time per player ([#64](https://github.com/UXPLIMA/uxm-lib/issues/64)) ([0abb6e2](https://github.com/UXPLIMA/uxm-lib/commit/0abb6e2066e3cfbc73b80f553f5fa013e0739799)), closes [#61](https://github.com/UXPLIMA/uxm-lib/issues/61)
* **network:** log the broker address as the operator wrote it ([#60](https://github.com/UXPLIMA/uxm-lib/issues/60)) ([bb798c2](https://github.com/UXPLIMA/uxm-lib/commit/bb798c21155878b2320cc0ba91a8118dac13e3b7)), closes [#59](https://github.com/UXPLIMA/uxm-lib/issues/59)


### Performance Improvements

* **redis:** share one pub/sub connection across subscriptions ([#58](https://github.com/UXPLIMA/uxm-lib/issues/58)) ([c00fd69](https://github.com/UXPLIMA/uxm-lib/commit/c00fd69cbc603658794a93df015b89bd12c67b3b)), closes [#56](https://github.com/UXPLIMA/uxm-lib/issues/56)


### Code Refactoring

* **common:** move the thread-hop helpers next to Scheduler ([#65](https://github.com/UXPLIMA/uxm-lib/issues/65)) ([f98c579](https://github.com/UXPLIMA/uxm-lib/commit/f98c5796aacfc11262b919a7b3ac897e2749c2ba)), closes [#62](https://github.com/UXPLIMA/uxm-lib/issues/62)
* **condition:** build the currencies on the wallets ([#63](https://github.com/UXPLIMA/uxm-lib/issues/63)) ([3d09e39](https://github.com/UXPLIMA/uxm-lib/commit/3d09e398c89c2c93dcd69d4ebd64e4cdf9b4bc9c)), closes [#51](https://github.com/UXPLIMA/uxm-lib/issues/51)

## [0.1.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.0.2...uxm-lib-v0.1.0) (2026-10-29)

### Added

- Currencies in `uxmlib-condition`: a plugin that prices in several balances at once (Vault, VaultUnlocked, PlayerPoints, EcoBits, Treasury, items, experience, a PlaceholderAPI value or a bridge to another plugin's economy) reads them from a `currencies.conf` the operator writes, in the operator's order, with each currency's own display format (`MoneyText`). A currency whose plugin is missing is listed as not ready instead of failing. See the Currencies section of `docs/user/conditions.md`.
- `uxmlib-network`, a new module that carries messages between the servers of a proxy network: straight through the proxy with a secret per server and every message signed (`DirectBus`, `DirectBroker`), or over Redis (`RedisNetworkBus`). It is part of `uxmlib-all` and `uxmlib-bom`.

### Fixed

- Two takes from one player's balance can no longer both pass when together they are more than the player has, and a server thread never waits on another take. A Treasury call that gets no answer in time is logged with the account and is never paid twice.
- A repeating task's handle now equals the other handles on the same task, so a plugin that removes its task from a map by handle finds it again.
- A nametag goes only to clients that see its player, so it is no longer left floating where a player stood.

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
