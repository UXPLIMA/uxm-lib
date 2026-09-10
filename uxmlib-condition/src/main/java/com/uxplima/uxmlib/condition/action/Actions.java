package com.uxplima.uxmlib.condition.action;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmlib.condition.ItemStore;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Factory for the built-in {@link Action} closures. Each method captures the static payload of a config action
 * (a MiniMessage template, a command line, a parsed sound spec) and returns a closure that, at run time,
 * resolves placeholders against the {@link ActionContext} and performs the native delivery: Adventure {@code
 * Audience} for text and sound, the context's {@link CommandSink}s for commands, the subject player for a
 * close. Splitting closure construction out of {@link ActionParser} keeps both types small.
 *
 * <p>Text actions are flagged {@code async()} true: rendering MiniMessage and calling {@code sendMessage}/
 * {@code sendActionBar} only touches an {@code Audience} and is thread-agnostic. Command, close and take actions are
 * sync (the default) because dispatching a command, closing an inventory and editing one must run on the
 * thread that owns the player.
 */
public final class Actions {

    private Actions() {}

    /** {@code [message] <template>}: render the template and send it to the target audience. */
    public static Action message(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.target().sendMessage(render(context, template)));
    }

    /** {@code [broadcast] <template>}: render the template and send it to the broadcast audience. */
    public static Action broadcast(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.broadcast().sendMessage(render(context, template)));
    }

    /** {@code [actionbar] <template>}: render the template into the target's action bar. */
    public static Action actionBar(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.target().sendActionBar(render(context, template)));
    }

    /** {@code [title] <template>}: show the template as a title to the target (empty subtitle). */
    public static Action title(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(
                context -> context.target().showTitle(Title.title(render(context, template), Component.empty())));
    }

    /**
     * {@code [subtitle] <title> | <subtitle> [fade-in stay fade-out]}: a title with a second line under it, and
     * the three times in seconds.
     *
     * <p>{@code [title]} sends an empty subtitle and the vanilla timings, which is the whole of what it can
     * say. A refusal that wants "Not enough keys" over "You need one more" had no way to write it, and neither
     * did a win that wants to stay on screen longer than half a second. The two verbs are separate rather than
     * one growing optional parts, because a line that reads {@code [title] Welcome} in a hundred shipped files
     * has to keep meaning what it meant.
     */
    public static Action subtitle(TitleSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return asyncText(context -> context.target()
                .showTitle(Title.title(
                        render(context, spec.titleTemplate()),
                        render(context, spec.subtitleTemplate()),
                        Title.Times.times(spec.fadeIn(), spec.stay(), spec.fadeOut()))));
    }

    /**
     * {@code [bossbar] <seconds> <colour> <overlay> | <text>}: a bar across the top of the screen for a while.
     *
     * <p>It is shown to whoever the action targets and taken down again when the time is up, so nothing has to
     * be tracked by the caller and a reload cannot leave a bar on a player forever. A bar is the one surface
     * that says "this is happening now" without taking the screen, which is why an interaction that lasts (a
     * crate opening, a job payout window, a cooldown) wants one and had nothing to write.
     */
    public static Action bossBar(BossBarSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return asyncText(context -> context.player().ifPresent(player -> {
            BossBar bar = BossBar.bossBar(render(context, spec.textTemplate()), 1.0f, spec.colour(), spec.overlay());
            player.showBossBar(bar);
            context.later(spec.duration(), () -> player.hideBossBar(bar));
        }));
    }

    /**
     * {@code [particle] <name> [count] [spread]}: a puff of particles where the player is standing.
     *
     * <p>The one visual an interaction can carry that is not text. A name the server does not know is skipped
     * rather than thrown, the same way an unparseable sound key is: an operator's typo in a cosmetic line may
     * not stop the message and the sound beside it from happening.
     */
    public static Action particle(ParticleSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return asyncText(context -> context.player().ifPresent(player -> {
            Particle drawn = particleNamed(context.resolve(spec.nameTemplate()));
            if (drawn == null) {
                return;
            }
            player.getWorld()
                    .spawnParticle(
                            drawn, player.getLocation(), spec.count(), spec.spread(), spec.spread(), spec.spread());
        }));
    }

    private static @Nullable Particle particleNamed(String written) {
        for (Particle particle : Particle.values()) {
            if (particle.name().equalsIgnoreCase(written.strip())) {
                return particle;
            }
        }
        return null;
    }

    /** {@code [console] <command>}: dispatch the resolved command through the console sink. */
    public static Action console(String commandTemplate) {
        Objects.requireNonNull(commandTemplate, "commandTemplate");
        return context -> context.consoleSink().dispatch(stripSlash(context.resolve(commandTemplate)));
    }

    /** {@code [player] <command>}: dispatch the resolved command through the player sink. */
    public static Action playerCommand(String commandTemplate) {
        Objects.requireNonNull(commandTemplate, "commandTemplate");
        return context -> context.playerSink().dispatch(stripSlash(context.resolve(commandTemplate)));
    }

    /** {@code [close]}: close the subject player's inventory, or do nothing when there is no player. */
    public static Action close() {
        return context -> context.player().ifPresent(player -> player.closeInventory());
    }

    /**
     * {@code [sound] <key> [volume] [pitch]}: play the parsed sound to the target. The key is resolved at run
     * time from a placeholder template, so it can be malformed (an uppercase letter, an empty or garbage
     * resolution). {@link Action} must not throw on delivery, so an unparseable key is skipped rather than
     * letting {@link Key#key(String)} raise {@link net.kyori.adventure.key.InvalidKeyException} and abort the
     * remaining actions in the list.
     */
    public static Action sound(SoundSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return asyncText(context -> {
            String resolved = context.resolve(spec.keyTemplate());
            if (!Key.parseable(resolved)) {
                return;
            }
            context.target()
                    .playSound(Sound.sound(Key.key(resolved), Sound.Source.MASTER, spec.volume(), spec.pitch()));
        });
    }

    /** A title, the line under it, and the three times a title is shown for. */
    public record TitleSpec(
            String titleTemplate, String subtitleTemplate, Duration fadeIn, Duration stay, Duration fadeOut) {

        public TitleSpec {
            Objects.requireNonNull(titleTemplate, "titleTemplate");
            Objects.requireNonNull(subtitleTemplate, "subtitleTemplate");
            Objects.requireNonNull(fadeIn, "fadeIn");
            Objects.requireNonNull(stay, "stay");
            Objects.requireNonNull(fadeOut, "fadeOut");
        }
    }

    /** A boss bar: what it says, what colour it is, how it is divided, and how long it stays. */
    public record BossBarSpec(String textTemplate, BossBar.Color colour, BossBar.Overlay overlay, Duration duration) {

        public BossBarSpec {
            Objects.requireNonNull(textTemplate, "textTemplate");
            Objects.requireNonNull(colour, "colour");
            Objects.requireNonNull(overlay, "overlay");
            Objects.requireNonNull(duration, "duration");
        }
    }

    /**
     * Give the target a potion effect.
     *
     * <p>Not async: a potion effect is a write to a living entity, so it belongs to the thread that owns
     * that entity. On Folia that is the difference between working and a thread check failure, and on Paper
     * it is the same thread either way.
     *
     * <p>An effect the server does not know is nothing rather than an exception. A resource pack, a version
     * or another plugin may add one, and a file that names one this server has not got should not stop the
     * rest of the list from running.
     */
    public static Action effect(EffectSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return context -> context.player().ifPresent(player -> {
            PotionEffectType kind = effectNamed(context.resolve(spec.nameTemplate()));
            if (kind == null) {
                return;
            }
            int ticks = (int) Math.max(1, spec.duration().toMillis() / 50L);
            player.addPotionEffect(
                    new PotionEffect(kind, ticks, spec.level() - 1, false, !spec.hidden(), !spec.hidden()));
        });
    }

    private static @Nullable PotionEffectType effectNamed(String written) {
        String wanted = written.strip().toLowerCase(java.util.Locale.ROOT);
        NamespacedKey key =
                wanted.indexOf(':') >= 0 ? NamespacedKey.fromString(wanted) : NamespacedKey.minecraft(wanted);
        if (key == null) {
            return null;
        }
        return Registry.EFFECT.get(key);
    }

    /**
     * One potion effect: which, for how long, how strong, and whether the swirls are hidden.
     *
     * <p>The level is written the way an operator reads a potion and not the way the server counts one:
     * level 1 is the ordinary effect. The conversion happens once, where the effect is applied.
     */
    public record EffectSpec(String nameTemplate, Duration duration, int level, boolean hidden) {

        public EffectSpec {
            Objects.requireNonNull(nameTemplate, "nameTemplate");
            Objects.requireNonNull(duration, "duration");
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("an effect lasts longer than nothing, got: " + duration);
            }
            if (level < 1) {
                throw new IllegalArgumentException("an effect level starts at 1, got: " + level);
            }
        }
    }

    /** A puff of particles: which, how many, and how far they scatter. */
    public record ParticleSpec(String nameTemplate, int count, double spread) {

        public ParticleSpec {
            Objects.requireNonNull(nameTemplate, "nameTemplate");
            if (count < 1) {
                throw new IllegalArgumentException("a particle count is one or more, got: " + count);
            }
            if (!Double.isFinite(spread) || spread < 0) {
                throw new IllegalArgumentException("a particle spread is a finite number of zero or more");
            }
        }
    }

    /**
     * {@code [take-money] [currency] <amount>}: take the amount from the context's {@link Wallet}. The take is
     * all or nothing: the wallet either pays the whole amount or the action throws {@link ActionCostException}
     * having spent nothing.
     */
    public static CostAction takeMoney(MoneyCost cost) {
        Objects.requireNonNull(cost, "cost");
        return new TakeMoneyAction(cost);
    }

    /**
     * {@code [give-money] [currency] <amount>}: pay the amount into the context's {@link
     * com.uxplima.uxmlib.condition.Wallet}.
     *
     * <p>Not a {@link CostAction}: it takes nothing, so a list holding one has nothing to check before it
     * runs. A wallet that refuses the payment throws {@link ActionCostException} rather than returning
     * quietly, for the reason that exception was written: a reward that paid nothing and said nothing is
     * exactly the defect this verb removes.
     */
    public static Action giveMoney(MoneyCost payment) {
        Objects.requireNonNull(payment, "payment");
        return new GiveMoneyAction(payment);
    }

    /**
     * {@code [take-item] <item> [amount]}: take the amount from the context's {@link ItemStore}. The take is
     * all or nothing: the store either consumes the whole amount or the action throws {@link
     * ActionCostException} having consumed nothing.
     */
    public static CostAction takeItem(ItemCost cost) {
        Objects.requireNonNull(cost, "cost");
        return new TakeItemAction(cost);
    }

    private static Component render(ActionContext context, String template) {
        return Text.mini(context.resolve(template));
    }

    private static String stripSlash(String commandLine) {
        String stripped = commandLine.strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    private static Action asyncText(Action delegate) {
        return new Action() {
            @Override
            public void run(ActionContext context) {
                delegate.run(context);
            }

            @Override
            public boolean async() {
                return true;
            }
        };
    }

    /**
     * The static structure of a {@code [take-money]} payload: the currency template and the amount template.
     * An empty currency names the wallet's own default currency.
     */
    public record MoneyCost(String currencyTemplate, String amountTemplate) {

        /** Canonical constructor null-checks both templates; neither is resolved until run time. */
        public MoneyCost {
            Objects.requireNonNull(currencyTemplate, "currencyTemplate");
            Objects.requireNonNull(amountTemplate, "amountTemplate");
        }
    }

    /** The static structure of a {@code [take-item]} payload: the item template and the amount template. */
    public record ItemCost(String itemTemplate, String amountTemplate) {

        /** Canonical constructor null-checks both templates; neither is resolved until run time. */
        public ItemCost {
            Objects.requireNonNull(itemTemplate, "itemTemplate");
            Objects.requireNonNull(amountTemplate, "amountTemplate");
        }
    }

    // Sync, like every other action that changes server state: an economy call may block and an inventory edit
    // belongs to the thread that owns the player, so the driver picks the lane rather than the closure.
    private record TakeMoneyAction(MoneyCost cost) implements CostAction {

        @Override
        public void run(ActionContext context) {
            double amount = amount(context);
            String currency = currency(context);
            if (amount <= 0 || !context.wallet().withdraw(context.player().orElse(null), currency, amount)) {
                throw new ActionCostException("cannot take " + describe(context));
            }
        }

        @Override
        public boolean affordable(ActionContext context) {
            double amount = amount(context);
            return amount > 0 && context.wallet().balance(context.player().orElse(null), currency(context)) >= amount;
        }

        @Override
        public String describe(ActionContext context) {
            String currency = currency(context);
            String rendered = context.resolve(cost.amountTemplate()).strip();
            return currency.isEmpty() ? rendered : rendered + " " + currency;
        }

        private String currency(ActionContext context) {
            return context.resolve(cost.currencyTemplate()).strip();
        }

        // A template that resolves to something that is not a number cannot name a price, so it reads as
        // unaffordable rather than as free: run() then refuses loudly instead of taking an accidental zero.
        private double amount(ActionContext context) {
            Double parsed = number(context.resolve(cost.amountTemplate()));
            return parsed == null ? -1 : parsed;
        }
    }

    // The mirror of TakeMoneyAction, and sync for the same reason: an economy call may block and the lane
    // is the driver's to pick.
    private record GiveMoneyAction(MoneyCost payment) implements Action {

        @Override
        public void run(ActionContext context) {
            double amount = amount(context);
            String currency = currency(context);
            if (amount <= 0 || !context.wallet().deposit(context.player().orElse(null), currency, amount)) {
                throw new ActionCostException("cannot pay " + describe(context));
            }
        }

        private String describe(ActionContext context) {
            String currency = currency(context);
            String rendered = context.resolve(payment.amountTemplate()).strip();
            return currency.isEmpty() ? rendered : rendered + " " + currency;
        }

        private String currency(ActionContext context) {
            return context.resolve(payment.currencyTemplate()).strip();
        }

        // A template that resolves to something that is not a number cannot name an amount, so it reads as
        // nothing rather than as a free pass: run() then refuses loudly instead of paying an accidental zero.
        private double amount(ActionContext context) {
            Double parsed = number(context.resolve(payment.amountTemplate()));
            return parsed == null ? -1 : parsed;
        }
    }

    private record TakeItemAction(ItemCost cost) implements CostAction {

        @Override
        public void run(ActionContext context) {
            int amount = amount(context);
            String item = item(context);
            if (amount <= 0 || !context.itemStore().take(context.player().orElse(null), item, amount)) {
                throw new ActionCostException("cannot take " + describe(context));
            }
        }

        @Override
        public boolean affordable(ActionContext context) {
            int amount = amount(context);
            return amount > 0 && context.itemStore().count(context.player().orElse(null), item(context)) >= amount;
        }

        @Override
        public String describe(ActionContext context) {
            return amountText(context) + " " + item(context);
        }

        private String item(ActionContext context) {
            return context.resolve(cost.itemTemplate()).strip();
        }

        private String amountText(ActionContext context) {
            return context.resolve(cost.amountTemplate()).strip();
        }

        private int amount(ActionContext context) {
            Double parsed = number(context.resolve(cost.amountTemplate()));
            if (parsed == null || parsed < 1 || parsed > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) Math.floor(parsed);
        }
    }

    private static @Nullable Double number(String raw) {
        try {
            double parsed = Double.parseDouble(raw.strip());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The static structure of a {@code [sound]} payload: the key template plus a volume and pitch. */
    public record SoundSpec(String keyTemplate, float volume, float pitch) {

        /** Canonical constructor null-checks the key template; volume and pitch are plain floats. */
        public SoundSpec {
            Objects.requireNonNull(keyTemplate, "keyTemplate");
        }
    }
}
