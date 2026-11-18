# Changelog

## [0.11.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.10.0...uxm-lib-v0.11.0) (2026-11-18)


### Features

* **board:** draw a stored head from a player's skin ([#118](https://github.com/UXPLIMA/uxm-lib/issues/118)) ([8b03f62](https://github.com/UXPLIMA/uxm-lib/commit/8b03f62cb51d9c7c6f9f7cfa1123920a8a1dde03)), closes [#113](https://github.com/UXPLIMA/uxm-lib/issues/113)

## [0.10.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.9.0...uxm-lib-v0.10.0) (2026-11-17)

Nothing breaks a caller of 0.9.0. A menu `input:` step can now ask for a secret, and an action handler can escape
the values a player typed with its own function.

### Added

- `secret = true` on a menu `input:` step: the answer is asked in a dialog or an anvil, never in chat or on a sign,
  and a Bedrock player still gets the form. `Continuation.Input` carries the flag (the four-argument constructor
  stays); the loader reads it and the writer writes it back.
- `TextInput.promptResolved(..., boolean secret, ...)`, a seven-argument `MenuTextPrompt.prompt` and
  `MenuTextPrompt.through(Supplier<TextInput>)`. A prompt that does not override the secret form cancels a secret
  step and logs `event=menu_secret_input_unsupported` rather than asking it in chat.
- `MenuActionContext.args(UnaryOperator<String> typed)` and `ActionArguments.resolve(args, ctx, typed)`: one pass
  that runs only typed arguments and typed locals through the caller's function, for a handler that needs another
  escape than MiniMessage (JSON, legacy text). A context built from already filled arguments answers them as they
  are, so escaping them is the builder's job.

### Fixed

- A menu whose title, item name or lore used `%argument_<name>%`, `%input%`, `%drag_*%` or a form field id was
  skipped at load as referencing unknown ids. Misspelled ids are still reported.

## [0.9.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.8.0...uxm-lib-v0.9.0) (2026-11-17)

Upgrading: a menu argument is now drawn as text in a menu line. A plugin that passed markup in an argument on
purpose names it in the new `Menus.open(viewer, id, subject, page, arguments, markupArguments, passthrough)`.
Create the `board_name` table with `name VARCHAR(SqlBoardStore.NAME_LENGTH)` (64). Mark a PIN or password prompt
with `InputRequest.asSecret()`. See `docs/user/items-and-menus.md` and `docs/user/boards.md`.

### Added

- Bedrock without Floodgate: with only Geyser-Spigot installed, `BedrockScreen.forServer` sends Cumulus forms
  through Geyser and `GeyserBedrockDetector` reads the xuid. Floodgate still wins when both are installed.
- `BedrockDetector.forServer(server, fallback)`, `BedrockScreen.forServer(server, fallback)` and `resolving(...)`:
  a plugin can supply its own Bedrock backend when the server has neither Floodgate nor Geyser.
- A relayable form model: `BedrockFormRequest`, `BedrockFormResponse`, `BedrockFormCodec`,
  `RelayedBedrockScreen` over a plugin `Transport`, and `CumulusForms.toForm` for the proxy side.
- `InputRequest.asSecret()`: a secret is asked in a dialog or an anvil, never in chat or on a sign.
  `TextInput.cancel(Player)` drops a pending prompt.
- `NameFill.intoConnected(line, name)` and `intoConnected(line, name, bedrockPrefix)` accept Floodgate-prefixed
  names; `NameFill.isConnectedName`.
- `BoardAnswers.headOf(UUID, String, String emptyHead)`, the instance `head(UUID, String)` and a constructor taking
  stored heads per player.
- `MenuCloseEvent`, fired when a chest menu closes for good or a Bedrock form is dismissed.
- `render.ValueSpot`, `MenuContext.withTypedLocals` / `withMarkupArguments`, `MenuActionContext.written(...)` with
  `textArg()` / `textArgs()`.

### Changed

- Menu arguments, prompt answers, Bedrock form values and `drag_*` values are escaped where they go into a menu line
  and are never read again as a placeholder. They stay raw where they are read as data (materials, amounts,
  conditions, action arguments). Actions resolve in one pass; open actions now also see menu locals.
- The built-in `message:` action draws its argument as text; `command:` is unchanged.
- `LastMenu.LastOpen` gains a `markupArguments` component; the three-argument constructor stays.
- Chat input capture listens to both chat events at the lowest priority, takes the line from the first, then
  cancels and empties both. It is best-effort hiding: use `asSecret()` for a real secret.
- A tap on a Bedrock form view can refresh it, refresh a slot or reset pagination, as a chest view can.
- The two-argument `BoardAnswers.headOf` is deprecated and returns Steve for accounts that are not Mojang accounts.

### Fixed

- Boards drew a stranger's head for an offline-mode or Bedrock holder.
- `SqlBoardStore` kept names over 32 characters only if the column allowed it, and could store a uuid as a name;
  it now keeps names of 1 to 64 characters and skips anything else.
- A skull built by name blocked a server thread on an uncached Mojang lookup, and showed Steve on offline-mode
  servers.
- A captured chat line (a PIN, for example) reached legacy `AsyncPlayerChatEvent` listeners.
- A packet write was left incomplete when a listener cancelled it.

## [0.8.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.7.0...uxm-lib-v0.8.0)

Upgrading: replace `requires(Cmd.permission(node))` and `requires(Cmd.visibility(node))` with `Gate.permission`
and `Gate.visibility`. A tree you register through `CommandRegistrar` is sealed for you; one you hand Paper yourself
goes through `CommandGates.seal` and one `GateVisibility` per plugin. Code that walks a tree to list what a sender
may see asks `CommandGates.shows`, not `canUse`. See `docs/user/commands.md`.

### Added

- `Gate`: who sees a command node and who may use it, kept apart. `Gate.permission`, `Gate.visibility`,
  `Gate.shownTo` (a word for some readers, usable by everyone), `Gate.when` (a condition that is not a permission)
  and `Gate.open`, joined with `and` and `or`. As a requirement a gate always passes, so a player who types a word
  they may not use is answered with the plugin's line instead of Brigadier's "Incorrect argument for command".
- `CommandGates.seal`: every executor and suggestion below a gate asks the gates on its path first and answers a
  refusal with a `CommandGates.Refusal`. `CommandGates.prune`, `visibleSuggestions` and `shows`.
- `GateVisibility`: one listener per plugin that trims each player's command list and the completions the server
  sends of what a gate hides from them.
- `CommandRegistrar.register` with a `Refusal`.

### Changed

- `CommandRegistrar` seals every tree it registers; without a `Refusal` the server's permission message answers.
- The annotation layer renders `@Permission` as gates and refuses with `CommandMessages.noPermission`; a branch's
  executor checks the class node as well as its own.
- A tree with a gate may not redirect: registering one throws.

### Deprecated

- `Cmd.permission` and `Cmd.visibility`: native requirements that answer with Brigadier's text.

## [0.7.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.6.0...uxm-lib-v0.7.0)

### Added

- `BedrockClient` and `BedrockDetector.client(player)`: a Bedrock player's device, input, UI layout, game version
  and language, read from Floodgate or Geyser. `BedrockDetector.namePrefix()` gives the Floodgate name prefix.
- `BedrockScreen.sendSimpleForm` takes an `onClose` callback, and `BedrockScreen.available()` tells whether
  Floodgate or Geyser can send a form at all.
- `PacketPipeline.receive(player, packet)`: hand the server a packet as if the client sent it. It returns false
  when the player has no channel, nothing reads after the pipeline's handler, or the event loop refuses the task.
- `BedrockMenuChoice`: a Bedrock player may choose chest menus over forms, and every plugin on uxmLib reads the
  same choice from the player's data. A write runs on the player's own thread, so it is safe on Folia.

### Changed

- `Menus` and `TextInput` open a form for a Bedrock player only when a form can be sent and the player has not
  chosen chests; otherwise they open the chest or anvil a Java player gets.

## [0.6.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.5.0...uxm-lib-v0.6.0)

### Added

- `FeatureFiles`: a large feature keeps its block in `<block>.conf` beside `config.conf`. `moveOut` takes the
  block out of an older `config.conf` as text with its comment, `bringUpToDate` merges each file's shipped keys
  (catalogues still honoured), and `readInto` lays the files under the config so every key keeps its path.
- `ConfigBackups`: the copy a merge keeps before it writes a file again goes to
  `backups/<name>-<yyyy-MM-dd-HHmmss>.conf` beside the file, the last five of each file.
- `DataFiles.resolve(folder, name)`: what a plugin writes for itself lives under `data/`; an old copy in the
  plugin's folder moves there the first time, with SQLite journal files.
- `StorageSettings.sqliteFile(folder)`: where the SQLite file of a block is.
- `MoneyText.of(money, lines)`: a `format`, `format-one` or short suffix may name an `@` line, drawn in the
  reader's language.

### Changed

- A merge no longer writes `<name>.bak` beside the file; the copy is in `backups/`, and an old `.bak` moves
  there the next time the file is merged. Breaking for a plugin whose log line names `config.conf.bak`.
- A SQLite file named by a `StorageSettings` block lives under `data/` and moves there on the next open. Breaking
  for a plugin that resolves the file path itself: use `StorageSettings.sqliteFile`.

### Fixed

- A command answers in the language the player chose, not the client's (#82).

## [0.5.0](https://github.com/UXPLIMA/uxm-lib/compare/uxm-lib-v0.4.0...uxm-lib-v0.5.0) (2026-10-30)


### Changed

- A board word may hold `_` as well: 1 to 32 of `a-z`, `0-9`, `-` and `_`, so a plugin whose ids hold an
  underscore (a job such as `deep_miner`) can name a board after them.
- `BoardAnswers` reads a key from its end: the field, the place and the period are its last parts and the board is
  everything before them. Keys of boards without an underscore answer exactly as before.

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
