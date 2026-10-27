# Configuration, storage and messaging

## Configuration

Typed configuration over Configurate (HOCON): config is data with IDE support, not string-keyed lookups.

```java
HoconConfig config = HoconConfig.load(dataFolder.resolve("config.conf"));

int limit = config.getInt("homes.limit", 3);

ConfigProperty<Integer> live = config.intProperty("homes.limit", 3);
live.onChange(value -> rebuildLimits(value));   // fires on reload when the value changes
config.reload();
```

```java
// Or map a whole file onto one @ConfigSerializable record, hot-reloaded as an atomic snapshot:
RecordConfig<Settings> settings =
        new RecordConfig<>(dataFolder.resolve("settings.conf"), Settings.class, Settings::new);

Settings current = settings.current();   // cached snapshot: cheap on the hot path
settings.reload();                        // swaps in the new value, or keeps the prior one on a parse error
```

## Storage

```java
Database db = Database.builder().sqlite(dataFolder.resolve("data.db")).build();  // SQLite default, WAL
Sql sql = new Sql(db);
sql.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, coins INTEGER)");

// Versioned migrations run exactly once each, in order:
new MigrationRunner(db).apply(List.of(
        new Migration(1, "init", "CREATE TABLE warps (name TEXT PRIMARY KEY)")));

// Injection-safe query builder with bound parameters:
Query top = SelectBuilder.from("players")
        .where("coins", ">=", 100)
        .orderByDescending("coins")
        .limit(10)
        .build();
List<String> leaders = sql.query(top, row -> row.getString("uuid"));
```

An operator names a backend rather than writing a URL. `StorageSettings` reads one block of a HOCON file
(`type` is `sqlite`, `mysql`, `mariadb` or `postgresql`; then `file`, `host`, `port`, `database`,
`username`, `password`, `table-prefix`, `pool-size`, `local-only`, `copy-from-file`, every key optional, a `port` of 0 meaning the
backend's own) and opens the pool with the right
URL and driver class:

```java
StorageSettings storage = StorageSettings.read(config, "storage", "data.db", "myplugin_");
Database db = storage.open(dataFolder, "myplugin");

// {prefix}warps in a file becomes myplugin_warps. The versions go in a history of this prefix's
// own, so two schemas can share a database, and a schema that used the shared default history
// before carries its versions over instead of running them twice. "warps" is a table only it has.
storage.migrate(db, MigrationResources.loadRequired(loader, "db/migration"), "warps");
```

A store that kept its rows in its SQLite file moves them when its block names a network database.
`StorageCopy.fromFile` opens the file the block names, brings it up to date, and copies every table
under the prefix into the target, in one transaction, parents before the tables that point at them. It
fills empty tables only and refuses with a reason otherwise, so it never merges two sets of rows. The
migration history and the notices table stay behind. `local-only = true` in the block keeps one server on
that file while the others share the network database. `copy-from-file = true` in the block asks for the
copy at start: call `StorageCopy.atStart` in place of the migration, with the same arguments. It migrates
the target, and when the schema was built there by this very start, it empties what the migrations seeded
and fills every table from the file. A database that held the schema before is left alone, so the key
left on copies nothing twice. `StorageCopy.refill` is that fill on its own.

```java
try {
    StorageCopy.Copied copied = StorageCopy.fromFile(storage, dataFolder, file -> storage.migrate(file, migrations, "warps"), db);
    log.info("copied " + copied.total() + " rows");
} catch (StorageCopy.Refused refused) {
    // refused.reason(): ALREADY_ON_FILE, NO_FILE, TABLE_MISSING, COLUMN_MISSING or TARGET_HOLDS_ROWS
}
```

`storage.describe(db)` is the line a health check prints: the backend, where it is, the prefix and the
schema version, as in `postgresql at db:5432/minecraft, prefix myplugin_, schema v4`. It only reads, so
asking a store nothing has migrated leaves it as it was, and the password is never in the line.

A server carries the SQLite and MySQL drivers and no other. `StorageDrivers.coordinates()` names the MariaDB
and PostgreSQL drivers as Maven coordinates for a Paper plugin loader, so a plugin fetches them rather than
shading a copy each. Write a migration once for all four backends: no `DOUBLE` (use `DOUBLE PRECISION`), no
`TINYINT`, no `IF NOT EXISTS` on an index, no default on a `TEXT` column.

`uxmlib-storage-testing` is a test dependency that proves it. A test marked `@EveryBackend` runs once on
SQLite and once each on MySQL, MariaDB and PostgreSQL servers it starts in containers, every time on an
empty database of its own:

```java
@EveryBackend
void aRowSurvivesARestart(RealDatabase real) {
    try (Database db = real.open()) {
        // build the schema under real.settings().tablePrefix(), write, read back
    }
}
```

`@EveryBackend` tags the test `real-database`, so a build can run these in a task of their own. Higher-level
helpers add a write-through / write-behind cache, a two-tier player-profile cache, and cross-server row sync.

## Cross-server messaging

`uxmlib-redis` is a lean binary pub/sub primitive for fanning a message across the nodes sharing one Redis,
with no relational dependencies. For cache invalidation tied to the storage stack, `uxmlib-storage` builds
its `DataSynchronizer` on the same idea: a `LocalDataSynchronizer` single-node default that a
`RedisDataSynchronizer` bridges across nodes when Redis is configured. `SqlDataSynchronizer` does the same
over a table in the database the nodes already share, for a network with no Redis: `publish` writes a row,
`poll` (or the timer `start` sets up) reads what the other nodes wrote, and old rows are swept as it goes.
A message is a hint to read again, never the only copy of a change. `DataSynchronizers.forStore(storage,
database, scheduler, every)` picks for a configured store: `DataSynchronizers.none()` for a SQLite file or a
zero interval, which tells nobody, the store's own handlers included, and a started `SqlDataSynchronizer` on
`<prefix>sync` for a database server.

```java
bus.subscribe("party-updates", frame -> applyUpdate(frame));   // RedisBus: opaque byte[] frames
bus.publish("party-updates", encode(update));                  // fail-degraded, auto-reconnecting
```

## Backup participation

A plugin that keeps state in memory has, at any moment, work that a file copy would miss. Register once, and
whatever is about to copy the server's files asks you to save first.

```java
// In your plugin, on enable.
BackupParticipants.register(plugin, () -> profiles.saveAll());

// In a backup tool, before it reads anything. The executor decides which thread a save runs on.
List<String> late = BackupParticipants.prepareAll(Duration.ofSeconds(20), mainThreadExecutor);
```

The contract is a plain `Runnable` on purpose. Every plugin relocates its shaded copy of uxmLib, so an
interface of ours would be a different class in each jar. `Runnable` comes from the boot class loader, so it
is the same type everywhere. Registrations are marked, and an unmarked `Runnable` service is never run.
