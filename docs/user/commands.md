# Commands

## Commands

Both styles register through Paper's Brigadier lifecycle. Use the annotation DSL for the common case, or
the `Cmd` facade when you need to hand-build a node tree.

```java
@Command(name = "money", description = "Manage balances")
class MoneyCommand {

    @Subcommand("pay")
    @Permission("money.pay")
    void pay(Sender sender, @Arg("target") String target, @Arg(value = "amount", min = 1) int amount) {
        sender.send(Text.mini("<green>Paid " + amount + " to " + target));
    }
}

AnnotatedCommands.register(plugin, new MoneyCommand());
```

```java
// Or build the tree yourself:
CommandRegistrar.register(plugin,
        Cmd.literal("ping").requires(Cmd.permission("x.ping"))
                .executes(ctx -> {
                    Sender.of(ctx.getSource()).send(Text.mini("pong"));
                    return Cmd.OK;
                }),
        "Replies with pong");
```

The annotation layer also covers `@Range`/`@Length` bounds, `@Cooldown` rate limits, `@PlayerOnly`, flags
and switches, async execution, and paginated help, with SPIs for custom argument resolvers, validators,
and conditions.

Every command and every branch of one is renameable, aliasable and disableable from `commands.conf`. Mark
the handler `@FromConfig` with a key, hand `ConfiguredCommands.replacer()` to the resolvers, and the file
decides the label:

```hocon
commands {
  uxmbackup {
    name    = "backup"
    aliases = ["bak"]
    subcommands {
      restore { enabled = false }   # drops the branch out of the tree: the server says it is unknown
      prune   = false               # keeps the branch and lets your handler say it is turned off
    }
  }
}
```

The two shapes do different things on purpose. The block form is replaced by nothing at registration, so
the branch never enters the command tree. The plain one-line form leaves it in, and the handler reads
`commands.isBranchEnabled(KEY, "prune")` and answers in its own words:

```java
if (!commands.isBranchEnabled(BackupCommand.KEY, "prune")) {
    messages.send(sender.bukkit(), Keys.COMMAND_TURNED_OFF, Text.placeholder("name", "prune"));
    return;
}
```

Which one an operator wants depends on whether they would rather the word be unknown or be refused. The
server builds its command tree once, at enable, and offers no supported way to take one branch out of a
live one, so answering is the only honest alternative to removing.

A command and each of its branches can also carry words for the readers of one language. Each block is a
list of words per language tag, a bare language such as `tr` or a language and country such as `pt-BR`:

```hocon
commands {
  plot {
    name              = "plot"
    aliases           = ["plots"]
    localized-aliases { tr = ["arsa"] }
    subcommands {
      claim { localized-aliases { tr = ["al"] } }
    }
  }
}
```

A root word is registered as an ordinary alias, so `/arsa` works for anyone who types it, the console and a
Bedrock player included. Only the command list a client is sent leaves it out for a player who reads another
language. A branch word is shown to, and answered for, the readers of its language and the console only,
because Brigadier sends a client exactly the branches it may use. "Reads" is the `LocaleSource` handed to
`ParamResolvers.locales(...)`, so a language a player chose inside the plugin counts, and
`LanguageChoices` sends that player their command list again when they choose. `AnnotatedCommands` registers
the listener itself, and a command with no such word gets none.

Everything the command layer says on its own behalf: the refusals, the argument rejections, the whole
generated help page down to the separator between a command and its description, goes through
`CommandMessages`, so a translated plugin answers each player in that player's own language and paints the
answer in its own palette:

```java
ParamResolvers resolvers = ParamResolvers.withDefaults()
        .messages(myMessages)                                   // implement only the lines you care about
        .locales(LocaleSource.ofDefault(Locale.forLanguageTag("tr")));

AnnotatedCommands.register(plugin, new HomeCommand(), resolvers);
```

A plugin that already has a message catalog needs none of that by hand: `CommandMessages.fromCatalogue(messages)`
reads every line from the catalog under the `CommandLine` keys (`command.player-only`, `command.help-header`, and
the rest), so the command layer is translated and painted with everything else, and an operator can re-word any
of it in their language file.

The template each key ships is plain MiniMessage in the vanilla colours, and it is meant to be replaced. A
default has to read correctly for a consumer who wires no style layer at all, and MiniMessage leaves a tag it
does not know as literal text, so a default written in one plugin's own vocabulary would reach that consumer's
players as the characters of a tag. Write your own template at these paths, in your own tokens, in your own
language files: the catalog wins over the default for every key it holds.

Each method receives the sender's locale and the *values*: the bad input, the allowed ones, the time left.
Never a finished English sentence, since no other language puts those words in the same order. Every
method has a default, so a plugin that ignores the seam keeps the English it always had.

The help page is worth overriding even in an English-only plugin: `helpCommand`, `helpSeparator` and
`helpDescription` are the only way to restyle it, because that text is generated inside this jar and no
style pass over a plugin's own resources can see it.
