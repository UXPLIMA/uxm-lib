# Core: text, languages, style and scheduling

The examples assume `com.uxplima.uxmlib.text.Text` and `net.kyori.adventure.text.Component` are imported.

## Text

`Text` is the single place a plugin turns a MiniMessage string into an Adventure `Component`.

```java
Component title = Text.mini("<gradient:#ff5555:#ffaa00>Welcome</gradient>");
Component greet = Text.mini("<gray>Hello, <player>!", Text.placeholder("player", name));

String plain = Text.plain(title);     // strip formatting for logs
String mm    = Text.serialize(title); // round-trip back to MiniMessage
```

Text a player typed is never a MiniMessage string. `paint` applies a style from your config to a body that
stays literal, so a chat message reading `<click:run_command:/op me>free rank</click>` is shown, not run:

```java
Component line = Text.paint(message, config.getString("chat.format-style")); // e.g. "<gray>"
```

## Languages

`LanguageResolver` answers which language a player reads, in one order: the language the server forces, the
player's own choice, their client's language when the server follows it, and the configured default. A plugin
reads the three settings from its own `config.conf` into `LanguageSettings`:

```hocon
language {
  default = "en"
  follow-client = true
  force = ""
}
```

A choice is shared. `LanguageResolver.choose` writes it on the player's own data under `uxmlib:language`, as a
plain language tag, so every plugin on the server built on uxmLib reads the language a player chose in any one
of them. A plain key is what makes this work: each plugin relocates its own copy of the library, so no class of
uxmLib is the same class in two plugins. A choice made for a player who is away waits in the plugin's own store
and is applied by `LanguageResolver.settle`, which a plugin calls when the player joins. A choice a plugin kept
before this was shared is shared the first time it answers.

The language files reach the disk through `LanguageFileUpdates.bringUpToDate(loader, "messages", folder)`, once
per start, before they are read. It writes a file the folder lacks, and it keeps a copy of each shipped file under
`messages/.defaults/`. On a later start a one-line value that still equals that copy is a line nobody touched, and
it takes the words the jar ships now; a line the operator changed is theirs. Only that line is rewritten, so the
file keeps its comments, order and line endings. A new key needs no write: `Languages.load(folder, locale, loader,
"messages")` reads the jar's file underneath the operator's.

## Style layer

A palette, the letters an interface is set in, and the tokens a message file writes instead of colours,
so a server changes its whole look by editing one file, and no plugin ships a hex code.

A theme has three layers. `palette` is the server's own colours under the server's own names, and nothing
outside that file speaks those names, so any of them may be renamed or dropped. `roles` is the shared
vocabulary a message writes: `accent`, `value`, `good`. Each role points at a palette name or a hex code,
and the map is open, so a server may add a job of its own and use it as a tag the same day. `wheel` is an
ordered list of colours that decoration is taken from, so a screen of tiles can differ tile by tile without
any file naming a colour.

```java
// One file for a suite of plugins, with this plugin's own file on top of it, key by key. The folder
// the suite shares is your word, not the library's; a plugin that stands alone reads its own file.
Theme theme = ThemeFiles.load(ThemeFiles.shared(dataFolder, "myTheme"), ThemeFiles.own(dataFolder));
Styler styler = new Styler(theme);

// A catalog line names a role, never a colour:
//   join.welcome = "<tag:'HOME'><body>Welcome back, <value><player></value>"
messages.reload(styler.style(catalog, MyKeys.values(), files, Locale.ENGLISH));

// On a /reload, hand the same styler the new palette rather than building a second one:
styler.reload(ThemeFiles.load(ThemeFiles.shared(dataFolder, "myTheme"), ThemeFiles.own(dataFolder)));

// Text a plugin computes has no catalog entry to style at load, so style it per viewer, and ask
// Messages which language that viewer is being served rather than reading the player's own locale,
// or a server that pins one gets catalog text in one language and computed text styled for another.
Component name = Text.mini(styler.apply(colour.displayName(), messages.localeOf(viewer)));
```

`Typography` converts a template to small capitals for the languages the theme names, leaving tags,
placeholders and anything inside `<plain>…</plain>` alone, so a translator writes ordinary words and nothing
rewrites the file they work in. It names none until your `theme.conf` does: a typeface is taste, and a
library that wrote your English in small capitals by itself would repaint a plugin that only wanted the
colours. `StyleTokens` then turns `<accent>`/`<value>` into the
theme's colours and `<tag:'HOME'>` into a bold category prefix. Both run once at load, not per message.

A role and a value share one namespace, and one name will always be both: `level` is a job a colour does and
it is also what anybody calls a level number. **A value that was supplied wins.** Where the pass runs at
render time (a written menu line, a tile) `CatalogueWords` hands it the row's values, so a bare `<level>` is
left for the value rather than painted and eaten. A line that closes the token, `<value>50</value>`, is
painting with the role and keeps it, because a value that is inserted has nothing to close. A catalogue is
styled once at load, where no value exists yet: name your own value names to
`styler.style(catalog, keys, files, locale, "level"::equals)` and the pass leaves them alone; say nothing and
`Messages` reports the collision at ERROR on the first render, naming the line and the token, instead of the
number quietly not being there. A token that is neither a role nor a value reaches the client as the
characters somebody typed, so a mistake is on the screen. See `docs/adr/0001`.

A value is inserted after that pass and is never converted, because a name, a nickname and a world are what
a player wrote. When a value is the interface talking instead (the name of an item, the word for a state),
write `<caps>…</caps>` around it and it is converted at render:

```
listed = "You listed <caps><item></caps> for <money><price>"   // the item is converted, the player is not
```

It is the mirror of `<plain>…</plain>` and it follows the same language list: turn small capitals off in
`theme.conf` and every `<caps>` goes quiet with the rest, so a screen is never half converted. Small
capitals are Latin only, so a language whose letters have no small-capital form simply does not write the
tag, which is why the choice is one line at a time rather than one switch for the server.

Menus draw from the same theme: `MenuTitles.centre` pads a window title into the middle of the frame,
`Tiles` puts a tile's title on the first lore line under a blank name (a single space: an empty one makes
the client fall back to the material's name) and paints it with a gradient the caller names, `LoreWrap` builds
the tooltip in one fixed order and wraps a description at a width the caller may set, and
`MenuSounds` reads the three menu sounds from config, and `MenuSoundsFile` keeps them read from one file and
reads it again on a reload. Lore an operator wrote in a config file goes through
`Lore.lines`, which reads every line as body text and gives it the same column, padding and closing air as
lore built here, so a plugin that ships items never has to write that geometry down as a house rule of its
own. A file that draws its own glyphs and its own margins goes through `Lore.verbatim` instead, which adds
the padding and the closing air and leaves the geometry alone, so a plugin can still let its buyer write a
look that is nothing like this one.

The library ships `uxmlib/theme.conf` as a starting file a consumer copies and then owns: a palette, the
roles pointed at it, and every other block written out but commented, so what it turns on is visible and
short. A key it leaves out keeps the shipped answer.

What ships is the mechanism, and not a look. Every role answers, so `<value>` is a colour rather than seven
characters a player reads, and each one falls back to one of the sixteen colours Minecraft has always had:
the one palette that is neither ours nor invented. Nothing else is decided. No glyph is drawn, no category
takes a colour of its own, no gradient and no wheel exist, and no language is written in small capitals,
until your file names them. A `gradients { header = [...] }` block paints every `<h:'…'>` header across
those stops; leave it out and headers stay the flat accent colour.

A plugin that stands alone wants one file and nothing beneath it, which is `ThemeFiles.load(file)`. The
two-file form is for a suite that agrees on a folder name.

A name that is not a heading takes `<g:'UXM Network':wheel>`: the same lookup as a heading, painted across
every colour of the wheel in order, with no bold and in the letters the file wrote. That is the one token a
server list line or a title screen usually wants, and it keeps the seven colours in the theme rather than in
the file.

A heading may take its colour three ways, and the file that draws it picks the one that fits. The wheel is
the one that needs no name: `Tiles.titled(theme, title, lore, position)` paints the tile at that position
with that arc of the wheel, so twelve tiles read as twelve headings and the layout file stays free of
colour. A content file reaches the same wheel by writing the position in the token, `<h:'REWARDS':4>`,
which is what a hotbar or a list written in configuration uses. A heading that always means the same thing
names a role instead, `<h:'REWARDS':good>`. A sweep of your own is a named gradient in the file,
`<h:'REWARDS':mint>`. The library never picks for you: which tiles look alike is a decision about one
interface, and only the file that knows what the tiles are about can make it.

## Scheduling (Folia-ready)

One `Scheduler` interface covers Paper's four schedulers; build it once and inject it everywhere. Every
method returns a cancellable `TaskHandle`, so your plugin never touches `BukkitScheduler` and runs
unchanged on Folia.

```java
Scheduler scheduler = new PaperScheduler(plugin);

scheduler.global(() -> broadcast());                                  // next tick, global region
scheduler.regionLater(location, Duration.ofSeconds(2), () -> grow()); // region-threaded
scheduler.entityTimer(player, Duration.ZERO, Duration.ofSeconds(1),
        handle -> { if (done) handle.cancel(); });                    // entity-threaded, repeating
scheduler.async(() -> fetchFromApi());                                // off the main thread
```
