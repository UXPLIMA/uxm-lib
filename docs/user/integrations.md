# Integrations, updates and schematics

## Integrations

Every third-party symbol is touched only past a plugin-present guard, so a server without the soft-dependency
still loads cleanly.

```java
// Economy: resolves Vault or VaultUnlocked, with a no-op dummy so call sites never null-check.
EconomyBridge.orDummy().deposit(player, 100);

// Permissions / ranks via LuckPerms, present-guarded:
LuckPermsHook.find().flatMap(lp -> lp.prefix(player)).ifPresent(prefix -> applyPrefix(prefix));

// PlaceholderAPI: a no-op pass-through without PlaceholderAPI installed:
String text = PlaceholderApi.apply(player, "Hi %player_name%");

// Region queries against WorldGuard or Towny behind one provider-agnostic contract:
RegionHooks regions = new RegionHooks();
WorldGuardRegionService.find().ifPresent(regions::register);   // present-guarded
boolean canBuild = regions.active()
        .map(region -> region.canBuild(player, location))
        .orElse(true);

// A transient toast that leaves no persistent advancement behind:
Toast.builder()
        .icon(Material.DIAMOND)
        .title(Text.mini("<gold>Objective complete!"))
        .show(player);

// A dependency-free Discord webhook (no JDA, no bot token):
new DiscordWebhook(url).sendEmbed(DiscordEmbed.colored("Alert", "Server started", 0x00FF00));
```

The PlaceholderAPI integration also has a write side: register a `PlaceholderProvider` to expose your own
`%uxm_<prefix>_<params>%` placeholders. A provider is asked about an `OfflinePlayer`, because that is what a
leaderboard line, a tab-list entry and a hologram name; narrow it with `instanceof Player` when the answer
needs a player who is here.

### One `uxm` expansion for every plugin

PlaceholderAPI keeps one expansion per identifier, and a second registration of `uxm` replaces the first. So a
plugin does not register `uxm` itself: it joins the one expansion every UXM plugin shares, with a prefix of its
own.

```java
PlaceholderRegistry registry = new PlaceholderRegistry()
        .register("balance", (player, path) -> balanceOf(player))
        .relational((viewer, target, path) -> canSee(viewer, target) ? "yes" : "no");

SharedPlaceholders.join(plugin, "essentials", registry);   // %uxm_essentials_balance%, %rel_uxm_essentials_...%
```

The registry is asked with what follows `<prefix>_`. A prefix is one word of lower-case letters and digits. The
first plugin to join registers the expansion, and every later one joins it. A plugin leaves when it is disabled,
with nothing to call; `join` also hands back how to leave sooner. When the plugin that registered the expansion
goes, the plugins that stay register their own in its place, and their placeholders keep answering. The expansion
survives `/papi reload`, and a PlaceholderAPI that lost it gets it back when it loads its expansions again.

A prefix another plugin holds is refused: the log names the plugin holding it, which keeps answering, and `join`
answers empty. So does a `uxm` held by an expansion that is not the shared one, such as a plugin registering `uxm`
on its own; that expansion is left in place. `PlaceholderExpansions.register(registry, author, version)`, which
did exactly that, is deprecated. `PlaceholderExpansions` stays for an identifier of the plugin's own.

Each plugin shades its own uxmLib, relocated and perhaps at another version, so no uxmLib class is the same class in
two plugins: a service keyed by one, or a cast to one, fails between them. The plugins meet on types they all load
from the server instead, with no reflection. The expansion PlaceholderAPI holds as `uxm` is a
`java.util.function.Supplier` of the member table, a `ConcurrentMap` from prefix to member. Each member is a
PlaceholderAPI `PlaceholderExpansion` that is never registered: its identifier is the prefix and its required
plugin the plugin that joined. Every uxmLib version keeps this contract, so plugins built on different versions
share one expansion.

## Update checker

Notify-only: it logs to the console and shows a permission-gated message on join. It never self-downloads.

It decides when to speak and never what is said. The sentence is an `UpdateAnnouncement` you supply, and it
is required rather than defaulted: a version notice is one of the few lines a plugin sends that has no entry
in its own message file, so a shipped one would put English, in colours nobody chose, into a plugin that
translates everything else.

```java
UpdateChecker checker = new UpdateChecker(
        scheduler, new GitHubReleaseProvider("you", "your-plugin"), UxmLibVersion.VERSION);

UpdateAnnouncement announcement = (name, current, release) -> messages.render(
                MyKeys.UPDATE_AVAILABLE,
                Text.placeholder("name", name),
                Text.placeholder("current", current),
                Text.placeholder("latest", release.version()))
        .clickEvent(ClickEvent.openUrl(release.url()));

new UpdateNotifier(plugin, scheduler, checker, "yourplugin.update.notify", announcement)
        .start(Duration.ofSeconds(40), Duration.ofHours(6));
```

## Schematics

A structure is a file the operator can make with whatever tool they like, and a plugin reads it with nothing
else installed. `SpongeSchematicReader` reads the Sponge format of WorldEdit and FAWE, versions 1 to 3, with
limits on its size checked before anything is held for it. `SpongeSchematicWriter` writes version 3.

```java
Schematic island = SpongeSchematicReader.withDefaults().read(Files.newInputStream(file));

SchematicPaster paster = new SchematicPaster(scheduler);
paster.paste(island, centre, PasteOptions.DEFAULT.withRotation(Rotation.CLOCKWISE_90))
        .thenAccept(report -> {
            if (!report.faithful()) {
                logger.warning("pasted with changes: " + report);
            }
        });

// Saving is the same in reverse: a box, the point to save it around, and a file.
new SchematicCapture(scheduler)
        .capture(world, cornerOne, cornerTwo, origin, CaptureOptions.DEFAULT)
        .thenAccept(saved -> write(saved, file));
```

A paste is safe on Folia. Each chunk is loaded without holding a thread, then set on the region that owns it,
a share of blocks a tick, so a large paste spreads over ticks and over regions. Blocks are set without physics.
A chest, a sign, a head, a banner, a spawner, a command block, a jukebox, a lectern, a beacon, a campfire, a
brushable block and a decorated pot keep what they held; another kind of block entity keeps its defaults and
is named in the report. Entities come through Paper's own serialisation and the server's data upgrade, so a
file from an older version reads as that version meant it.
