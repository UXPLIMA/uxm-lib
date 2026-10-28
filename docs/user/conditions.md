# Conditions and actions

## Conditions & actions

A config-driven gate plus a config-driven action list: both parsed once and run many times.

```java
// Resolve %...% through an injected resolver, compare, and collect a failure message:
ConditionList gate = ConditionList.of(
        PlaceholderCondition.parse("%player_level% >= 30"),
        Text.mini("<red>You need level 30"));
boolean allowed = gate.test(ConditionRequest.forPlayer(player));

// Named actions parsed once into closures and run in order:
ActionList.parse(List.of(
        "[message] <green>Hi %player_name%",
        "[console] heal %player_name%")).run(context);
```

A plugin that fills the player's name into a line itself, before the engine reads it, goes through
`NameFill`. The server can be told to let a name hold a space, a bracket, a quote or a brace, and such a
name would add an argument to a command, open a tag in a message, close a tag's argument or reach the
`{delay=..}` grammar. No escape covers every place a name can land, so `NameFill` fills `%player%`,
`<player>`, `{player}` and `%player_name%` only with a name an account could have, and returns nothing
for a line that names any other:

```java
for (String line : lines) {
    NameFill.into(line, player.getName())
            .ifPresentOrElse(
                    filled -> ActionList.parse(List.of(filled)).run(context),
                    () -> log.warn("skipped, the name is not one an account could have: {}", line));
}
// A plain command with no verb in front of it reads the same way:
NameFill.into("give %player% diamond", player.getName()).ifPresent(console::dispatch);
```

`MoneyCondition`, `[take-money]` and any plugin that owes a player money read through a `Wallet`, and
`com.uxplima.uxmlib.condition.wallet`
ships the backends. Each one is soft: the plugin manager is asked before any type of another plugin is
named, the handle is resolved on first use and kept, and a missing or renamed API reads zero and refuses
rather than throwing. A server with none of these plugins behaves as though the package were not there.

```java
// An economy described rather than compiled against. Vault, VaultUnlocked, PlayerPoints and EcoBits are
// shipped descriptions; a fifth economy is one more EconomyBinding and needs nothing from this library.
Wallet vault = BridgedWallet.ofServer(Economies.vault(), log);
Wallet points = BridgedWallet.ofServer(Economies.playerPoints(), log);

// Treasury answers a subscriber instead of returning, so it is written out with a bound on the wait.
Wallet gems = TreasuryWallet.ofServer(Duration.ofSeconds(2), log);

// The last resort: a balance behind a placeholder, taken by a console line, and paid by a second one.
Wallet tokens = PlaceholderWallet.ofServer(PlaceholderWallet.Pool
        .of("%tokens_balance%", "tokens take {player} {amount}")
        .paying("tokens give {player} {amount}"));

// The player's own experience, which needs no plugin at all. Points and levels are two currencies:
// a level costs seven points near the start and over a hundred past level thirty one.
Wallet xp = ExperienceWallet.ofPoints();
Wallet levels = ExperienceWallet.ofLevels();
```

`ExperienceWallet` is the one backend with no other plugin behind it, so it has no present-guard and
nothing to log. It reads and writes the player's own experience bar, which means the caller must already
be on the thread that owns the player (the region that owns them on Folia), exactly as `[take-item]`
requires for the inventory. A player who is not online reads zero and pays nothing.

A backend answers what the balance is, whether a whole amount can be taken, and whether a whole amount can
be paid in, and nothing else. It fixes no price, no cost table and no currency name: those are the game a
plugin plays. Which currency name maps to which backend is the plugin's choice too, so routing between
several is the plugin's own `Wallet`.

Every take is all or nothing. Where the economy refuses an overdraft itself, its refusal is read straight
back; where it cannot (EcoBits adjusts a balance and answers nothing, a console line answers nothing at
all), the balance is read first and the take is refused before anything moves.

`Wallet.deposit` is the mirror, for a plugin that owes a player a wage, a refund or a prize. It carries the
same all-or-nothing promise and reads nothing first, because a payment has no overdraft to refuse. It is a
`default` that refuses, so a wallet written against the older contract keeps compiling and says plainly
that it cannot pay out; every backend here overrides it. What a backend needs is one more thing named
beside the take: `depositPlayer` for Vault, `deposit` for VaultUnlocked, `give` for PlayerPoints, a second
console line for `PlaceholderWallet.Pool`. EcoBits names none, because its take is a give of a negative
number and `EconomyBinding.give()` reads that off `Calls.takeNegates`. An economy that names no give, or
that renamed the one it named, goes on reading and taking and refuses only the payment.

## Currencies

A wallet is one backend. A plugin that prices things in several, such as coins, points, levels and
diamonds at once, reads them from a `currencies.conf` the operator writes, through the
`com.uxplima.uxmlib.condition.currency` package:

```hocon
currencies {
  coins {
    type = "vault"          # vault, vaultunlocked, playerpoints, ecobits, treasury,
    order = 10              # item, experience, bridge or placeholder
    enabled = true
    display { format = "{amount} coins", format-one = "{amount} coin", decimals = 2, thousands = "," }
  }
  experience { type = "experience", order = 30, unit = "levels" }
  diamonds   { type = "item", order = 40, material = "diamond" }
}
```

```java
List<CurrencySpec> specs = CurrencyFile.read(config, "uxmShop");    // no server needed
List<Currency> built = CurrencyBuilder.build(specs, items, players, scheduler, economies,
        placeholders, console, known, log);
CurrencyRegistry currencies = new CurrencyRegistry(built);          // the operator's order
Wallet wallet = new CurrencyWallet(currencies);                     // for [give-money] and friends
MoneyText text = MoneyText.read(config);                            // "1,250 coins", "1 coin"
```

- `order` decides the preferred currency (the lowest) and the order of a menu's tabs. HOCON forgets the
  order a section was written in, so the file says it; two equal orders fall back to the id.
- A currency whose plugin is missing is built but not ready. `CurrencyRegistry.ready()` leaves it out
  and a price in it cannot be paid until the plugin is there. Nothing fails to start over one.
- Amounts are `Money`: a currency id and a `BigDecimal`, so a price never drifts by a rounding error.
  `isWhole()` currencies (experience, items) refuse fractions.
- Every method takes a player id, because the other side of a trade is often offline. `paysOffline()`
  says whether an absent player can be paid directly; when it cannot, the plugin keeps the money for them.
- `bridge` describes an economy no shipped description covers (its class, its take and give methods)
  and `placeholder` reads a balance through a placeholder and moves it with two console lines, so an
  operator with another economy plugin needs nothing new from the library.
