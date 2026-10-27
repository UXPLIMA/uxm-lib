# Holograms, HUD and packets

## Holograms

Holograms are built on native `Display` entities (Text / Item / Block): no packets, no per-version
NMS, and managed for you so visibility, per-viewer content, and cleanup are handled automatically.

```java
HologramManager holograms = new HologramManager();
holograms.installLifecycleListener(plugin);   // resets per-player state on quit / world change

Hologram spawn = holograms.spawn(
        Holograms.builder()
                .line(Text.mini("<yellow><bold>Spawn"))
                .line(Text.mini("<gray>Welcome to the server"))
                .billboard(Display.Billboard.CENTER)
                .glow(Color.YELLOW),
        location);
```

On top of that base the module adds a distance-driven visibility `HologramPool`, per-player widgets
(paged, switchable, live leaderboard), entity-following holograms, text animation (typewriter / scroll),
a Mojang skin resolver for player-head displays, and holograms defined in HOCON.

A real entity is the right answer when an operator has to walk into the hologram and click it. It is the
wrong answer for decoration: it is in the entity count, it is written to the region file, it can be reached
by anything that reaches entities, and a chunk unload takes it away. `PacketHologram` in `uxmlib-packet` is
the other shape. It is sent and never spawned, so the server holds no entity at all.

```java
HologramPackets packets = new NmsHologramPackets(new PacketSender(new ChannelResolver()));

PacketHologram hologram = PacketHologram.show(
        packets,
        scheduler,
        location,
        HologramAppearance.defaults().withBillboard(Display.Billboard.CENTER),
        viewer -> List.of(Text.mini("<yellow>Market"), Text.mini("<gray>right-click the chest")),
        32.0,                          // how near a viewer must stand
        Duration.ofMillis(500));       // how often the audience and the text are recomputed

hologram.remove();                     // takes it off every client and stops the loop
```

Every frame runs on the anchor's region thread and asks the world who is within the view distance. A player
who arrives is sent the spawn frame, a player who leaves is sent a remove, and a player who stays is sent
fresh text, so a per-viewer line stays current. While nobody is near, no packet is written at all.

## HUD overlays

Adventure-native overlays delivered through Paper's own player API: no packets, no NMS.

```java
// Flicker-free sidebar: only the lines that actually changed are re-sent.
SidebarManager sidebars = new SidebarManager(Bukkit.getScoreboardManager());
Sidebar sb = sidebars.create(player, Text.mini("<gold><bold>Server"));
sb.lines(List.of(
        Text.mini("<gray>Online: <white>42"),
        Text.mini("<gray>Map: <white>spawn")));
sb.show();

new Titles().show(player, Text.mini("<green>Welcome"), Text.mini("<gray>have fun"));
new Tablist().set(player, header, footer);

new ActionBarManager(scheduler, server).show(player, Text.mini("<yellow>Saved!"), Duration.ofSeconds(3));
new BossBarManager(scheduler, server).countdown(player, Text.mini("<red>Event"), Duration.ofMinutes(1));
```

A player may belong to exactly one scoreboard team, so plugins that each create their own teams overwrite
one another's nametags without saying so. `NametagRegistry` owns the team instead, and every plugin hands it
a contribution:

```java
NametagRegistry nametags = new NametagRegistry(
        new ScoreboardNametagSink(Bukkit.getScoreboardManager().getMainScoreboard(), getLogger()),
        getLogger(),
        " ",        // what goes between two contributed parts
        scheduler); // the scoreboard belongs to the global region

nametags.contribute(player, NametagContribution.prefix("uxmTags", priorityFromConfig, Text.mini("<gold>[VIP]")));
nametags.contribute(player, NametagContribution.color("uxmGlow", priorityFromConfig, NamedTextColor.RED));

nametags.withdraw("uxmTags"); // onDisable: take back only your own parts
```

Prefixes and suffixes compose in priority order (smaller is earlier), and priority decides layout. The name
has a single colour, so one claim has to win, and which one is your server's rule rather than the library's:
pass a `ComposedNametag.ColorOwner` as the fifth constructor argument. `newest()` is the default and gives
the colour to whoever asked last, so a glow a player just switched on wins over a rank tag that has sat
leftmost all session. `byPriority()` gives it to the smallest priority instead, which is what an estate that
sells a rank colour wants. Write your own for anything else. Every claimant is named once in the log,
together with the winner.
Read your priority from your own config: which of two plugins comes first is an operator's decision. A team
uxmLib did not create is never touched, so a third-party plugin managing its own teams is left alone.

That settles the fight inside one jar. Every plugin relocates its own copy of uxmLib, so two plugins each
constructing a `NametagRegistry` is two registries and two teams, and the second one still loses. Claim it
through `SharedNametags` instead and one server has one registry, whichever plugin loaded first:

```java
Nametags nametags = SharedNametags.claim(this, () -> new NametagRegistry(
        new ScoreboardNametagSink(board, getLogger()), getLogger(), " ", scheduler));

nametags.contribute(player, NametagContribution.prefix(getName(), priority, prefix));

// onDisable
nametags.withdraw(getName());
SharedNametags.release(this);
```

The first plugin to load builds the registry and offers it on the server's own service manager under
`java.util.function.Function`, which comes from the boot class loader and is therefore the same class in
every jar; the payload is JDK, Bukkit and Adventure types, none of which is ever shaded. The registration is
marked, so a `Function` service registered for somebody else's purpose is left alone. This is the same
technique `BackupParticipants` uses for a save request.

Two consequences worth knowing. The plugin that builds the registry decides the separator and the colour rule
for the whole server, so read both from your own config and expect an operator to set them on whichever
plugin loads first. And when an owner is disabled the server unregisters its service, so the next plugin to
call takes the registry over: names come back as plugins contribute again rather than staying gone until a
restart.

## Experimental: packet layer

`uxmlib-pipeline`, `uxmlib-packet`, and `uxmlib-nametags` are an in-progress, **clean-room** packet foundation
for the things the public API cannot do per viewer: different nametag colours, tab-list rows, or holograms
for different players. PacketEvents (the off-the-shelf choice) is GPL, so none of it is borrowed; the Netty
plumbing is re-implemented for Paper 26.2+ and the unavoidable NMS is quarantined to single, named classes
behind pure ports built against the Mojang-mapped dev bundle.

Those classes are compiled against the Mojang-mapped server they run on, so every internal they touch is a
checked fact rather than a reflected guess. There is no adapter layer and no per-line seam: one server line
means the compiler covers the whole surface, and a server internal that moves fails the build rather than
someone's server.

One of them is worth calling out on its own, because every plugin that writes something onto an item wants
it. `com.uxplima.uxmlib.packet.item` rewrites the item a *client* is shown and never the item the server
holds. A plugin implements `ItemView`, hands it to `ItemViews`, and the five clientbound packets that carry a
tooltip (the container slot, the container contents, the cursor item, the player-inventory slot, the trade
list) go through it on the way out. The stack in the chest is untouched, so no save can catch a decorated
copy and no second plugin can read one back. The package holds the mechanism only: what the view writes,
lore or a name or nothing at all, is the plugin's.

These modules have **unstable APIs** and parts are still landing. Treat them as a preview; the stable
toolkit above does not depend on them.
