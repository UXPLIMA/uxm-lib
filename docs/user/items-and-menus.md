# Items, menus and forms

## Items

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
        .name(Text.mini("<gradient:#ff5555:#ffaa00>Flameblade</gradient>"))
        .lore(Text.mini("<gray>A legendary weapon"))
        .enchant(Items.enchantment("sharpness"), 5)   // enchantments are registry entries
        .flags(ItemFlag.HIDE_ENCHANTS)
        .unbreakable(true)
        .build();

ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
        .skull(SkullData.ofName("Notch"))
        .build();

String saved = ItemSerialization.toBase64(sword);  // survives every data component
ItemStack back = ItemSerialization.fromBase64(saved);

// A menu icon is a button, and the client does not know that: it writes its own lines under the lore,
// so a leather chestplate used as a button says "Dyed" and "Armor" beneath whatever the menu wrote.
ItemStack button = ItemBuilder.of(Material.LEATHER_CHESTPLATE)
        .name(Text.mini("<white>Cosmetics"))
        .vanillaTooltip(false)          // or hiddenComponents(...) to keep one of them
        .build();
```

`ItemConfig` reads the same spec from HOCON, `hide-vanilla-tooltip = true` included, so an operator writing
a menu file reaches for that rather than for the deprecated `HIDE_ADDITIONAL_TOOLTIP` flag. Leave it off for
an item a player owns: on a kit sword or a vault icon the damage and enchantment lines are the point.

It also reads the six blocks that belong to one kind of item, in the same keys and the same tokens a menu
file's `decor` block already uses:

```hocon
material = POTION
potion {
  type    = strength                # the base potion, a registry id
  color   = "#00AAFF"               # the bottle tint
  effects = ["speed:1:600"]         # effect:amplifier:durationTicks
}
leather-color = "#A1FF33"           # dyed leather: hex, an "r,g,b" triple, or a dye name
firework {
  power   = 2                       # flight duration, 0 to 127
  effects = ["ball_large:#ff0000,#ffff00:#ffffff:flicker,trail"]
}                                   # type:colours:fade-colours:flags; the last two are optional
trim   { material = diamond, pattern = sentry }
banner { patterns = ["stripe_top:red", "border:white"] }   # laid on in the order written
spawner = zombie                    # the mob inside a spawner
```

A value that names nothing the server knows throws and says which value it was. That is on purpose and it is
where this parts company with the menu renderer, which skips a token it cannot resolve: a menu with one wrong
tile still opens, while an item read once at load and then sold must not quietly become a water bottle.

## GUIs

Install the framework once in `onEnable`, then build menus fluently. Clicks are cancelled by default: an
unconfigured menu can never leak items.

```java
Guis.install(plugin, scheduler);   // the Scheduler overload enables animation and auto-refresh

SimpleGui menu = Guis.gui().title(Text.mini("<dark_aqua>Menu")).rows(3).build();
menu.filler().fillBorder(GuiItem.display(pane));        // border / row / column / rect / fill helpers
menu.set(2, 5, GuiItem.button(icon, e -> click()));     // 1-indexed row, col
menu.onClose(event -> persist());
menu.open(player);

// Paginated, scrolling, and non-chest shapes:
PaginatedGui shop = Guis.paginated().title(Text.mini("Shop")).rows(6).build();
products.forEach(p -> shop.addPageItem(GuiItem.button(p.icon(), e -> buy(p))));
shop.open(player);

ScrollingGui list = Guis.scrolling(ScrollType.VERTICAL).rows(4).build();

// A storage menu holds real items (take/place allowed) and keeps them across opens:
StorageGui vault = Guis.storage().rows(3).build();
vault.onClose(e -> save(vault.contents()));

// Per-viewer items: dynamic (computed per player), stateful (first matching state), animated:
menu.set(4, GuiItem.dynamic(ctx -> headOf(ctx.viewer())));
menu.set(5, GuiItem.stateful()
        .display(ctx -> ctx.viewer().hasPermission("vip"), vipIcon)
        .display(ctx -> true, normalIcon)
        .build());
menu.set(6, GuiItem.animated(List.of(frame1, frame2), Duration.ofMillis(250)));

// Multi-screen navigation with a back-stack:
GuiNavigator nav = new GuiNavigator();
nav.open(player, mainMenu);
subMenu.set(8, GuiItem.back(nav, backArrow));

```

A menu written in a file is the [menu engine](#menu-engine)'s work, not this module's. `uxmlib-gui`
draws what code puts in a window; `uxmlib-menu` reads the file that says what goes in it.

The two seams this module keeps for it are the words. `GuiText` is where a consumer answers what a key
stands for and how a written line reads, and `CatalogueWords` is the answer for a consumer whose words
are in a message catalogue: a key is looked up in the language of the viewer, a plain line is painted in
the roles of the theme, and a line that starts with `tile:` is drawn as the whole tooltip through
`MenuTiles`. The library parses no text of its own, so it decides no look and holds no language.

A tile line takes its closing sentence from `<key>.action` under the block it names, and a word written as
`action:@<key>` on that line takes it from the key named there instead. A tile whose click answers a
different thing in each state is then one line and one block of words rather than one whole block per
state, and the sentence stays a catalogue line, so it keeps the colour it is written in. The state may be a
`%token%` inside the key, because the engine fills a token in before the line is read as a tile.

A word on that line is a fact and reads in the value colour, which is right for a number, a name or a
duration and wrong for a word that means something is good or bad. `state:<fact>` draws that row through
`Lore.status` instead: the theme's `status` glyph, and a value that is not painted in the value colour.
`state:<fact>:<role>` names the role of `theme.conf` the value takes when it carries none of its own, and
that role may be a `%token%`, so a plugin answers `good` or `bad` and the theme says what either one looks
like. A value the catalogue already coloured keeps its own colour, and a role the theme does not hold
paints nothing. A value still goes into a catalogue line as text and never as markup, so a player who
named their item `<red>` reads those characters on the tile and repaints nothing.

> **Removed in 0.46.0.** `com.uxplima.uxmlib.gui.config` shipped in 49 of this library's 50 tags, so it is
> the largest published surface any release has removed. It is gone: `MenuConfig.load`, `MenuSpec.read`,
> `MenuDraw`, `MenuActions`, `MenuConditions`, `MenuLists`, `MenuFiles`, `MenuAction`,
> `MenuActionRunner`, `MenuSlots`, `IconOptions`, `ConfigEditorGui` and `ConfigValueEditor` were the
> smaller, older menu readers, and `uxmlib-menu` replaces all of them. Two classes of that package
> survive under new names: `gui.config.CatalogueWords` is `gui.CatalogueWords` and now implements
> `GuiText`, and `gui.config.MenuTiles` is `gui.style.MenuTiles`. A consumer on `MenuConfig.load` has no
> automatic upgrade: the mask shape and the engine's shape are different models, not different
> spellings, and an action or a condition named in an old file points at a handler registered in Java
> rather than at anything the engine can resolve. Move the file to the engine's shape and register the
> vocabulary through `MenuBindings`.
>
> One piece of that package was working code rather than an old reader, and it kept its own home:
> `MenuActionRunner` was the only place that resolved the constant spelling of a sound, the
> `BLOCK_NOTE_BLOCK_PLING` an operator copies out of the API. That is `gui.style.SoundNames` now. A
> consumer that registers a `sound` action for the engine resolves the name through it, and `MenuBasics`
> is the registration most consumers want, so a file that writes a constant keeps playing what it always
> did.

## Menu engine

`uxmlib-gui` draws a menu. `uxmlib-menu` reads one. A menu is a file, and the engine is what turns that
file into a window a player clicks through. A consumer writes no layout code at all: it registers the
names its menus may say and do, and everything else lives in HOCON that an operator owns.

```java
// The whole vocabulary a file may reach. A name that is not registered here does not exist.
MenuBindings bindings = new MenuBindings();
bindings.action("shop:buy", ctx -> buy(ctx.viewer(), ctx.subject(Offer.class)));
bindings.condition("shop:can-afford", (ctx, args) -> balance(ctx.viewer()) >= price(ctx));
bindings.placeholder("shop:balance", ctx -> format(balance(ctx.viewer())));
bindings.list("shop:offers", ctx -> offersFor(ctx.viewer()));
bindings.pagedList("shop:archive", (ctx, page) -> archivePage(ctx.viewer(), page));  // asks the store per page

ItemRenderer items = new ItemRenderer(guiText, this::theme, bindings.placeholders());
MenuRenderer renderer = new MenuRenderer(items, bindings.conditions(), bindings.contents());
// The confirm window's rows, slots and blocks are a look, so they are the plugin's to choose; with none set
// it is three rows, lime wool at 11 and red wool at 15. Its buttons read gui.confirm.yes and gui.confirm.no.
Menus menus = Menus.builder(renderer, scheduler, bindings.lists())
        .confirmLook(new ConfirmLook(3, 11, Material.LIME_WOOL, 15, Material.RED_WOOL))
        .build();

// The click, page and refusal sounds the engine plays are the plugin's file's, read at each gesture.
// A listener handed no sounds plays none: the library has no tones of its own.
MenuSoundsFile sounds = MenuSoundsFile.read(dataFolder.resolve("config.conf"), "menu.sounds");
MenuListener listener = MenuListener.builder(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin)
        .feedback(sounds)
        .build();

// close, open:<menu>, command:<line>, message:<line> and sound:<name> <volume> <pitch>: the five verbs
// that mean the same thing in every menu of every plugin. They are a call and never automatic, so a
// plugin that wants its own keeps the names and registers those instead.
MenuBasics.register(bindings, menus);

// The operator's file when they have one, the bundled copy when they do not.
MenuSpec shop = MenuSpecs.loadOrBundled("menus/shop.conf", dataFolder, 6, log);
menus.registerSpec("shop", shop);

// A file that names an action, a condition, a placeholder, or a list source that nobody registered is
// reported here, with every offending name, rather than on the click that would have reached it.
List<String> problems = bindings.validate(List.of(shop));

menus.open(player, "shop", null);                  // no subject
menus.open(player, "offer", offer, 2);             // a subject and a page
menus.back(player);                                // the back stack the engine keeps per viewer
menus.confirm(player, Text.mini("<red>Sell everything?"), this::sellAll, () -> menus.reopenLast(player));
menus.openEditor(player, editorSpec, offer);       // a property editor over one object
```

The file carries the layout, the words, the conditions, and the actions:

```hocon
title = "@shop.title"
rows = 6
open-requirement = ["shop:unlocked"]
open-actions = ["sound:ITEM_BOOK_PAGE_TURN 0.7 1.2"]

items {
  filler { slots = ["0-53"], material = GRAY_STAINED_GLASS_PANE, name = " ", priority = 0 }

  offers {
    slots = ["10-16", "19-25"], priority = 5
    list {
      source = "shop:offers"
      page-size = 14
      sorts = ["price", "name"]
      template {
        material = "%offer_icon%", name = "@shop.offer", lore = ["<gray>%offer_price%"]
        view = ["shop:can-afford"]          # who sees this row at all, asked once per entry
      }
    }
  }

  buy {
    slot = 49, material = EMERALD, name = "@shop.buy", priority = 10
    permission = "shop.buy"
    view = ["shop:can-afford"]
    click {
      left  = { do = ["shop:buy", "close"], requirements = ["shop:can-afford"], deny = ["message:@shop.poor"] }
      right = { do = "confirm:shop:buy-all", title = "@shop.confirm-all" }
      shift-left = { do = "input:shop:buy-amount", prompt = "@shop.amount" }
    }
  }

  next  { slot = 53, material = ARROW, type = next, priority = 10 }
  first { slot = 45, material = MAP, type = jump, to-page = 1 }
}

bedrock {
  title = "@shop.title"
  widgets = [{ type = dropdown, name = offer, label = "@shop.offer", options = ["Sword", "Shield"] }]
  on-submit = ["shop:buy"]
}
```

What the engine adds over a plain layout reader:

- **Paged and scrolling lists.** A slot range is filled from a registered source, sorted by the names the
  file asks for, and paged over the range. A paged source is asked for one page rather than for everything.
  A `view` block on the template gates one row, asked once per entry with that entry in context; a `view`
  on the list item gates the whole list. A refused row is dropped rather than left as a hole, so the rows
  that remain close up and the page count follows them, and the Bedrock form reads the same two gates. An
  entry that implements `PageBreak` ends the page it meets rather than taking a slot on it, which is how a
  list of runs that belong together is drawn one run to a page. A `next` or `previous` arrow, and a `jump`
  to the page its `to-page` names (counted from one), is drawn on every page, and one with no page to go to
  does nothing when pressed. A file that wants it only when it turns a page writes `view = ["has-next"]`,
  `["has-previous"]` or `["has-page:3"]` on it, and may put anything under it for the other case, such as a
  grey dye gated on `!has-next`. A list's `empty { }` item is drawn in each cell a page leaves without an
  entry, and `%position%` in it, or in the template, is the place of that cell in the whole list, counted
  from one: a leaderboard writes `#%position% nobody yet`. `align = center` puts the short last row of a
  page in the middle of its row.
- **A fill with no holes.** The `fill-item` is painted into every slot nothing else drew when the window
  is drawn: a list cell with no entry and no `empty` item, an item its `view` hid. A content region is
  the viewer's and is never painted. An item written `material = AIR` keeps its slot empty on purpose.
- **An expression language.** Any rendered line may carry a `{math: ...}` block, evaluated after the
  placeholders in it are substituted: `{math: %price% * %amount%}`, `{math: min(%stock%, 45)}`. Seven
  functions are callable and nothing else (`min`, `max`, `abs`, `floor`, `ceil`, `round`, `sqrt`), and a
  block that cannot be read renders blank rather than leaking its own text at a player.
- **A requirement and an action chain on each of the eight click gestures** (left, right, the two shifted
  forms, middle, drop, control-drop, and double-click), with a `deny` fallback, a `delay`, and a `chance`
  on any single action. The chain runs in order on the viewer's own thread, and an action that calls
  `MenuActionContext.refuse()` stops the ones after it, so a cost can be an action of its own rather than
  something the verb that gives has to fold into itself. An action carrying a `delay` is waited off the
  chain and cannot stop it.
- **Continuations.** An `input:` or a `confirm:` action splits the chain: the rest of it runs when the
  viewer answers, through the same anvil, chat, sign, or dialog input `uxmlib-gui` installs.
- **A property editor.** Text, number, toggle, enum, colour, and list properties over one object, each
  with its own picker window, so a plugin that edits a configuration object writes no menu for it.
- **Icons from other plugins.** An icon spec may name a head, a serialized stack, or an item held by
  ItemsAdder, Oraxen, Nexo, CraftEngine, MMOItems, ExecutableItems, HeadDatabase, or EntryStack. Every
  such lookup sits behind a plugin-present guard and falls back to a plain material when the plugin is not
  there.
- **A Bedrock viewer sees a form.** When the spec carries a `bedrock` block and the viewer is a Bedrock
  player, the engine sends the native form instead of the chest. See [Bedrock forms](#bedrock-forms).
- **The file is written as well as read.** `MenuSpecWriter` turns a `MenuSpec` back into the HOCON the
  loader parses, so a plugin can ship an in-game menu editor without carrying a writer of its own. It is
  model faithful rather than byte faithful: see [Known gaps](#known-gaps).

```java
// The header is the consumer's text, never the library's: it names your commands and your folder.
MenuSpecWriter writer = new MenuSpecWriter(readMyHeaderResource());

String hocon = writer.write(edited);          // render, for a preview or a diff
writer.write(edited, dataFolder.resolve("menus/shop.conf"));   // render and save, temp file then rename

// File IO behind a menu click never runs on the main thread.
scheduler.async(() -> writer.write(edited, file));
```

The model under all of this names no platform type at all: a spec parses, validates, and is asserted on
with no server running.

The engine names no colour and no wording. `@shop.title` is a key in the consumer's own catalogue, and the
colours come from the consumer's `Theme`. The library ships neither.

## Bedrock forms

A Bedrock client cannot see a chest menu the way a Java client does, so a plugin that draws a menu has two
questions: is this viewer a Bedrock player, and how do I send them a form. `uxmlib-bedrock` answers both,
and the menu engine uses it for you.

```java
BedrockDetector bedrock = BedrockDetector.forServer(server);   // NONE on a Java-only server
BedrockScreen screen = BedrockScreen.forServer(server);

if (bedrock.isBedrock(player.getUniqueId())) {
    screen.sendSimpleForm(player, "Shop", "Pick a category", List.of(
                    new BedrockButton("Swords", new BedrockImage(BedrockImage.Kind.PATH, "textures/items/iron_sword")),
                    new BedrockButton("Shields", null)),
            index -> scheduler.entity(player, () -> openCategory(player, index)));

    screen.sendCustomForm(player, "Buy", null, List.of(
                    new BedrockWidget.Dropdown("offer", "Offer", List.of("Sword", "Shield"), 0),
                    new BedrockWidget.Slider("amount", "Amount", 1, 64, 1, 1)),
            answers -> buy(player, answers), () -> {});
}
```

Four form kinds are covered: simple (a list of buttons), modal (two buttons), input (one text field), and
custom (any widget list). `BedrockIcons.forMaterialSpec` turns a material name or a head reference into a
button image, so a menu file that already names an icon needs no second Bedrock-only spelling.

Both interfaces follow one shape. Each has a `NONE` constant that names no SDK type and a `forServer`
factory that returns a backed implementation only when Geyser or Floodgate is enabled. The backed
implementations are package-private, so `org.geysermc` is named in exactly two files and reached only
inside the enabled branch. On a Java-only server the factories answer `NONE`, no Geyser or Floodgate class
is ever loaded, and a caller never has to guard the call itself. A response from Cumulus arrives off the
main thread, so a callback hops back onto the viewer's entity thread before it touches the world.

## Known gaps

- **A saved menu keeps its model and loses its file.** `MenuSpecWriter` writes every shape
  `MenuSpecLoader` reads, and reading it back gives the same model. What it cannot keep is the
  file the operator typed: a comment is dropped, key order is the writer's own, and a shorthand
  (`fill-item`, `update-interval`, a `pattern` template) comes back as the canonical form of the
  model it produced. A plugin that offers an in-game editor tells its operators to keep their own
  notes somewhere the editor does not rewrite.
