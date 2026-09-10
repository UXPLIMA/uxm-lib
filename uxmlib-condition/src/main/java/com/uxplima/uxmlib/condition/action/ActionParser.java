package com.uxplima.uxmlib.condition.action;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.bossbar.BossBar;

/**
 * The pure parser turning one config action string into a {@link ParsedAction}. The grammar is a bracketed
 * prefix naming the {@link ActionType}, optionally followed by a payload:
 *
 * <pre>{@code
 *   [message] <green>Hello, %player_name%!
 *   [console] give %player_name% diamond 1
 *   [sound] minecraft:entity.player.levelup 1.0 1.2
 *   [close]
 * }</pre>
 *
 * <p>A verb may be followed by a brace block naming when and how often it runs. Every part of it is optional
 * and a line without one behaves exactly as it always did. See {@link ActionModifiers}.
 *
 * <pre>{@code
 *   [message] {delay=40} <accent>Two seconds later
 *   [broadcast] {if=%player_level% >= 10} <accent>A veteran won something
 *   [sound] {repeat=3, every=5} UI_BUTTON_CLICK 1 1
 * }</pre>
 *
 * <p>Parsing happens once, at load: the returned closure carries the static payload and only resolves
 * placeholders at run time. An unknown prefix, a missing {@code ]}, or a payload-less string for a type that
 * needs one all raise {@link IllegalArgumentException} with a message naming the offending input, so a config
 * loader can surface the line to the author.
 */
public final class ActionParser {

    private ActionParser() {}

    /** Parse one action string into its type, payload and closure. */
    public static ParsedAction parse(String line) {
        Objects.requireNonNull(line, "line");
        String trimmed = line.strip();
        if (!trimmed.startsWith("[")) {
            throw new IllegalArgumentException("action must start with a [prefix]: " + line);
        }
        int close = trimmed.indexOf(']');
        if (close < 0) {
            throw new IllegalArgumentException("action prefix is missing its closing ']': " + line);
        }
        String keyword = trimmed.substring(1, close).strip();
        ActionType type = ActionType.fromPrefix(keyword);
        // The brace block is optional and comes between the verb and the payload. A line without one reads
        // exactly as it did before this feature, which is why every file already written keeps working.
        ActionModifiers.Parsed modified = ActionModifiers.parse(trimmed.substring(close + 1));
        String payload = modified.payload();
        requirePayload(type, payload, line);
        return new ParsedAction(
                type, payload, ModifiedAction.of(build(type, payload), modified.modifiers()), modified.modifiers());
    }

    private static void requirePayload(ActionType type, String payload, String line) {
        if (type.payloadRequired() && payload.isEmpty()) {
            throw new IllegalArgumentException("action [" + type.prefix() + "] needs a payload: " + line);
        }
    }

    private static Action build(ActionType type, String payload) {
        return switch (type) {
            case MESSAGE -> Actions.message(payload);
            case BROADCAST -> Actions.broadcast(payload);
            case ACTIONBAR -> Actions.actionBar(payload);
            case TITLE -> Actions.title(payload);
            case SUBTITLE -> Actions.subtitle(parseTitle(payload));
            case BOSSBAR -> Actions.bossBar(parseBossBar(payload));
            case PARTICLE -> Actions.particle(parseParticle(payload));
            case CONSOLE -> Actions.console(payload);
            case PLAYER -> Actions.playerCommand(payload);
            case CLOSE -> Actions.close();
            case SOUND -> Actions.sound(parseSound(payload));
            case EFFECT -> Actions.effect(parseEffect(payload));
            case TAKE_MONEY -> Actions.takeMoney(parseMoney(payload, "take-money"));
            case GIVE_MONEY -> Actions.giveMoney(parseMoney(payload, "give-money"));
            case TAKE_ITEM -> Actions.takeItem(parseItemCost(payload));
        };
    }

    /**
     * {@code <title> | <subtitle> [fade-in stay fade-out]}: the two lines, then the three times in seconds.
     *
     * <p>The pipe separates the lines because a title is words and a space cannot: "Not enough keys | You need
     * one more" is one line an operator reads at a glance. The times are optional and default to the vanilla
     * half a second, three and a half, one.
     */
    private static Actions.TitleSpec parseTitle(String payload) {
        int pipe = payload.indexOf('|');
        String head = pipe < 0 ? payload.strip() : payload.substring(0, pipe).strip();
        String rest = pipe < 0 ? "" : payload.substring(pipe + 1).strip();
        List<String> tail = new ArrayList<>(List.of(rest.isEmpty() ? new String[0] : rest.split("\\s+")));
        Duration fadeIn = Duration.ofMillis(500);
        Duration stay = Duration.ofMillis(3500);
        Duration fadeOut = Duration.ofSeconds(1);
        List<String> times = new ArrayList<>();
        while (tail.size() > 1 && isNumber(tail.get(tail.size() - 1)) && times.size() < 3) {
            times.add(0, tail.remove(tail.size() - 1));
        }
        if (times.size() == 3) {
            fadeIn = seconds(times.get(0), payload);
            stay = seconds(times.get(1), payload);
            fadeOut = seconds(times.get(2), payload);
        }
        return new Actions.TitleSpec(head, String.join(" ", tail), fadeIn, stay, fadeOut);
    }

    /**
     * {@code <seconds> <colour> <overlay> | <text>}: how long the bar stays, what colour it is, how it is
     * divided, and what it says. The colour and the overlay are optional and default to white and a solid bar.
     */
    private static Actions.BossBarSpec parseBossBar(String payload) {
        int pipe = payload.indexOf('|');
        if (pipe < 0) {
            throw new IllegalArgumentException(
                    "[bossbar] is written '<seconds> [colour] [overlay] | <text>', got: " + payload);
        }
        List<String> head = List.of(payload.substring(0, pipe).strip().split("\\s+"));
        String text = payload.substring(pipe + 1).strip();
        Duration duration = seconds(head.get(0), payload);
        BossBar.Color colour =
                head.size() > 1 ? named(BossBar.Color.values(), head.get(1), BossBar.Color.WHITE) : BossBar.Color.WHITE;
        BossBar.Overlay overlay = head.size() > 2
                ? named(BossBar.Overlay.values(), head.get(2), BossBar.Overlay.PROGRESS)
                : BossBar.Overlay.PROGRESS;
        return new Actions.BossBarSpec(text, colour, overlay, duration);
    }

    /** {@code <name> [count] [spread]}: which particle, how many, and how far they scatter, in blocks. */
    private static Actions.ParticleSpec parseParticle(String payload) {
        List<String> parts = List.of(payload.strip().split("\\s+"));
        int count = parts.size() > 1 ? (int) parseFloat(parts.get(1), "count", payload) : 12;
        double spread = parts.size() > 2 ? parseFloat(parts.get(2), "spread", payload) : 0.4;
        return new Actions.ParticleSpec(parts.get(0), count, spread);
    }

    private static <E extends Enum<E>> E named(E[] values, String written, E fallback) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(written.strip())) {
                return value;
            }
        }
        return fallback;
    }

    private static boolean isNumber(String written) {
        try {
            Double.parseDouble(written);
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /**
     * {@code <effect> <seconds> [amplifier] [hidden]}: what to give, for how long, how strong, and whether
     * the swirls are hidden.
     *
     * <p>The amplifier is written the way an operator reads a potion rather than the way the server counts
     * one: level 1 is the ordinary effect, level 2 is the second tier. Writing zero here would be a command
     * an operator copied from vanilla producing nothing, so 1 is the default and the conversion happens once.
     */
    private static Actions.EffectSpec parseEffect(String payload) {
        List<String> parts = tokenize(payload);
        if (parts.size() < 2 || parts.size() > 4) {
            throw new IllegalArgumentException(
                    "action [effect] takes <effect> <seconds> [level] [hidden], got: " + payload);
        }
        int level = parts.size() >= 3 ? (int) parseFloat(parts.get(2), "level", payload) : 1;
        if (level < 1) {
            throw new IllegalArgumentException("an effect level starts at 1, got: " + payload);
        }
        boolean hidden = parts.size() == 4 && Boolean.parseBoolean(parts.get(3));
        return new Actions.EffectSpec(parts.get(0), seconds(parts.get(1), payload), level, hidden);
    }

    private static Duration seconds(String written, String payload) {
        return Duration.ofMillis(Math.round(parseFloat(written, "seconds", payload) * 1000d));
    }

    /**
     * Split a {@code [take-money]} payload into its currency and amount. One token is the amount alone, which
     * spends the wallet's default currency; two tokens name the currency first. A literal amount is checked
     * now so a typo is a load error rather than a run-time refusal an operator meets in production.
     */
    private static Actions.MoneyCost parseMoney(String payload, String verb) {
        List<String> parts = tokenize(payload);
        Actions.MoneyCost cost =
                switch (parts.size()) {
                    case 1 -> new Actions.MoneyCost("", parts.get(0));
                    case 2 -> new Actions.MoneyCost(parts.get(0), parts.get(1));
                    default ->
                        throw new IllegalArgumentException(
                                "action [" + verb + "] takes <amount> or <currency> <amount>, got: " + payload);
                };
        requirePositiveLiteral(cost.amountTemplate(), verb, payload);
        return cost;
    }

    /**
     * Split a {@code [take-item]} payload into its item and amount. One token is the item alone and costs
     * one; two tokens name the amount second. A literal amount is checked now for the same reason.
     */
    private static Actions.ItemCost parseItemCost(String payload) {
        List<String> parts = tokenize(payload);
        Actions.ItemCost cost =
                switch (parts.size()) {
                    case 1 -> new Actions.ItemCost(parts.get(0), "1");
                    case 2 -> new Actions.ItemCost(parts.get(0), parts.get(1));
                    default ->
                        throw new IllegalArgumentException(
                                "action [take-item] takes <item> or <item> <amount>, got: " + payload);
                };
        requirePositiveLiteral(cost.amountTemplate(), "take-item", payload);
        return cost;
    }

    // A template holding a placeholder can only be checked once it resolves, so only a literal is validated
    // here. A zero or negative literal cost is always a mistake: it reads as a price and charges nothing.
    private static void requirePositiveLiteral(String amountTemplate, String prefix, String payload) {
        if (amountTemplate.indexOf('%') >= 0) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountTemplate);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "action [" + prefix + "] amount is not a number in: " + payload, notANumber);
        }
        if (!(amount > 0)) {
            throw new IllegalArgumentException("action [" + prefix + "] amount must be above zero in: " + payload);
        }
    }

    /**
     * Split a {@code [sound]} payload into its key template and optional volume/pitch. Volume defaults to
     * {@code 1.0} and pitch to {@code 1.0}; a non-numeric volume or pitch is a parse error so a typo is caught
     * at load rather than swallowed at run time.
     */
    private static Actions.SoundSpec parseSound(String payload) {
        List<String> parts = tokenize(payload);
        String key = parts.get(0);
        float volume = parts.size() > 1 ? parseFloat(parts.get(1), "volume", payload) : 1.0f;
        float pitch = parts.size() > 2 ? parseFloat(parts.get(2), "pitch", payload) : 1.0f;
        return new Actions.SoundSpec(key, volume, pitch);
    }

    // A manual whitespace tokenizer rather than String.split, which Error Prone flags for surprising behaviour
    // on trailing empties; this collapses runs of whitespace and never yields an empty token.
    private static List<String> tokenize(String payload) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (Character.isWhitespace(c)) {
                flush(tokens, current);
            } else {
                current.append(c);
            }
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static float parseFloat(String value, String field, String payload) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("sound " + field + " is not a number in: " + payload, notANumber);
        }
    }
}
