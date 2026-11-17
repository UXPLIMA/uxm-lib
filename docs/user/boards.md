# Leaderboards

`uxmlib-board` ranks players in the grammar every UXM plugin shares, so a hologram line written for one plugin's board
works for another's.

## Boards

```java
Board kills = Board.counter("kills");     // adds every event
Board best = Board.record("best");        // keeps the best single value
Board balance = Board.snapshot("balance", balances);  // what players hold now, all time only
Board xp = Board.counter("xp").withAllTime(levels);   // periods count, all time reads the snapshot
```

A word takes 1 to 32 lower-case letters, digits, `-` and `_`; a key is read from its end, so `top_my_job_weekly_1_name`
is the board `my_job`. The plugin's `board_name` column must hold the longest word. A `Snapshot` answers `top`, `countByScore` (both on the async lane, may block) and `scoreOf` (from memory).

## Wiring

```java
BoardSettings settings = BoardSettings.read(config, "leaderboard");
SqlBoardStore store = new SqlBoardStore(database, storage.table("board"), storage.table("name"), logger);
Leaderboard leaderboard = new Leaderboard(store, List.of(kills, best), this::nameOf, () -> settings, clock);
Standings standings = new Standings(store, leaderboard);
BoardRecorder recorder = new BoardRecorder(store, leaderboard, standings, scheduler::async, clock);
BoardAnswers answers = new BoardAnswers(leaderboard, standings, words, settings::emptyHead);

registerListener(new BoardPresence(standings, store));
scheduler.asyncTimer(Duration.ZERO, settings.refresh(), task -> leaderboard.refresh());

recorder.add(killer, kills, 1);
```

Boards that change while the plugin runs, one per track an operator configures for instance, go in as a
`Supplier<List<Board>>` instead of a list. It is read at every refresh and every answer: a board a reload adds shows
from the next refresh, and one it takes away stops answering at once.

The plugin creates the `{prefix}board` and `{prefix}name` tables in its own migrations; the `SqlBoardStore` Javadoc
gives their shape. An add is an upsert that sums (a counter) or keeps the higher score (a record) in the database
itself, so an event for a player who is away, or one counted on another server sharing the database, is kept.
SQLite, MySQL, MariaDB and PostgreSQL are supported.

## Placeholders

Hand `BoardAnswers` what follows the plugin's prefix:

| Placeholder | Answer |
|---|---|
| `top_<board>_<period>_<n>_name` | the player at place n, or the word for nobody |
| `top_<board>_<period>_<n>_value` | their score as `BoardText.score` shows it, or the word for none |
| `top_<board>_<period>_<n>_head` | their stored head, else their name or id for a Mojang account, else the empty head |
| `rank_<board>_<period>` | the reader's place, or the word for none |
| `value_<board>_<period>` | the reader's score |
| `resets_<period>` | the time until the period starts again, or the word for none for all time |
| `head` | what the reader's own head is drawn from, by the same rule |

A head tag hands a name or an id to the client, which asks Mojang about it. So only an account Mojang made (a version
4 uuid) is drawn by its name, or its id when an account cannot have that name. An offline-mode account (version 3) or a
Bedrock one through Floodgate (version 0) is drawn as the empty head, never as whoever holds its name at Mojang. A
plugin that keeps players' skins passes them as the last argument of `BoardAnswers`, a texture path per player read
from memory, and that is drawn first for every account.

Periods are `alltime`, `monthly`, `weekly` and `daily`. A board, period or field the plugin does not run answers
null, which PlaceholderAPI leaves as written. The words come from the plugin through `BoardText`. Its `score` can show
all time another way than the periods, a level where the periods show the experience earned in them.
