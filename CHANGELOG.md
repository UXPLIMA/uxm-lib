# Changelog

## [0.4.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.3.0...uxm-lib-v0.4.0) (2026-10-30)

### Added

- `Leaderboard` takes a `Supplier<List<Board>>` as well as a list, for boards that change while the plugin runs,
  such as one per track an operator configures. A board a reload adds shows from the next refresh; one it takes
  away stops answering at once.
- `BoardText.score(Board, BoardPeriod, long, UUID)`, a default method, so a plugin can show all time another way
  than the periods. `BoardAnswers` asks it for `top_..._value` and `value_...`.

### Changed

- A board word takes 1 to 32 characters (was 16). The plugin's `board_name` column must hold the longest.

## [0.3.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.2.0...uxm-lib-v0.3.0) (2026-10-30)

### Added

- `uxmlib-board`: leaderboards in the grammar every UXM plugin shares. `Board.counter`, `Board.record` and
  `Board.snapshot` (all time only), and `withAllTime` for a counter whose all-time board reads a snapshot.
  `BoardSettings.read(config, "leaderboard")` reads the standard `leaderboard` block. `SqlBoardStore` keeps
  boards in the plugin's own `{prefix}board` and `{prefix}name` tables on SQLite, MySQL, MariaDB and
  PostgreSQL, adding in the database itself so an event for a player who is away is kept. `Leaderboard`,
  `Standings`, `BoardRecorder`, `BoardAnswers` (the `top_`, `rank_`, `value_`, `resets_` and `head`
  placeholders, worded through `BoardText`) and `BoardPresence` (loads scores on pre-login). The module is in
  the BOM and in `uxmlib-all`. See `docs/user/boards.md`.

## [0.2.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.1.0...uxm-lib-v0.2.0) (2026-10-29)

Upgrading: change the imports of `RegionCall`, `GlobalCall`, `OnlinePlayers` and `Hop` to
`com.uxplima.uxmlib.scheduler`, and a `PlaceholderWallet.Reader` of your own to `read(UUID, String)`.
Callers of `CurrencyBuilder` need no change.

### Changed

- `RegionCall`, `GlobalCall`, `OnlinePlayers` and `Hop` moved from `com.uxplima.uxmlib.condition.currency`
  to `com.uxplima.uxmlib.scheduler` in `uxmlib-common`, beside `Scheduler`.
- `PlaceholderWallet.Reader` reads by player id: `read(UUID player, String placeholder)`, so a balance can
  be read for a player who is offline. `ServerPlaceholders` does the same.
- The placeholder, experience and Treasury currencies are built on the wallets of the same name, so a fix
  to a wallet reaches the currency too.
- `TreasuryWallet` never opens an account to read a balance or take; only a payment opens one. A move
  Treasury leaves unanswered within the bound reads as not taken, or as paid, and is logged with the
  account.

### Added

- `PlaceholderWallet` and `TreasuryWallet` take a player id and a `BigDecimal`: `balanceExact`,
  `withdrawExact`, `depositExact` and `reaches`, as `BridgedWallet` already did.
- `PlaceholderWallet.Names`, for a console line that needs the player's name.
- `TakeLocks`: one take at a time per player. A worker thread waits up to 30 seconds; a server thread is
  refused rather than kept waiting.
- `KnownPlayers.ofServer()`: online players first, then the ones on disk, never Mojang.

### Fixed

- Two takes from the same player at once could both pass the balance check on an economy that cannot
  refuse an overdraft itself (EcoBits, a console line). The wallets now take one at a time per player.
- The Treasury currency and wallet had drifted: the wallet opened an account to read a balance and read
  an unanswered payment as refused, so a caller could pay twice.
- The Redis bus opened a connection per subscription; it now shares one.
- The direct bus logs the broker as `host:port`, as the operator wrote it.

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
