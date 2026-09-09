package com.uxplima.uxmlib.condition.action;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The conditions under which one action runs, and how often.
 *
 * <p>An action list was a flat list of verbs: every line ran, once, immediately, for everybody. That is the
 * shape three products in this market moved past years ago, and it is what forces an operator to write two
 * lists and a permission check in the plugin's own code to say "and if they are a first timer, say this too".
 *
 * <p>Written as a brace block right after the verb, and every part of it is optional:
 *
 * <pre>{@code
 *   [message] <accent>Hello
 *   [message] {delay=40} <accent>And this, two seconds later
 *   [broadcast] {if=%player_level% >= 10} <accent>A veteran won something
 *   [sound] {repeat=3, every=5} UI_BUTTON_CLICK 1 1
 *   [player] {chance=0.25} spawn
 *   [console] {if=money vault >= 500, delay=20} say rich
 * }</pre>
 *
 * <p>The condition is one line of the same grammar every requirement in this family uses, so an operator
 * learns it once. A delay and a repeat are in ticks, which is the unit every other number a Minecraft
 * operator writes is in.
 *
 * @param delay how long before the action runs, zero for at once
 * @param repeat how many times it runs, at least one
 * @param every how long between repeats, ignored when it runs once
 * @param condition a line that has to hold for it to run at all, or nothing
 * @param chance the probability it runs, between zero and one
 */
public record ActionModifiers(Duration delay, int repeat, Duration every, Optional<String> condition, double chance) {

    /** One tick, which is the unit an operator writes a delay in. */
    private static final Duration TICK = Duration.ofMillis(50);

    private static final ActionModifiers NONE =
            new ActionModifiers(Duration.ZERO, 1, Duration.ZERO, Optional.empty(), 1);

    public ActionModifiers {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(every, "every");
        Objects.requireNonNull(condition, "condition");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("a delay is not negative, got: " + delay);
        }
        if (every.isNegative()) {
            throw new IllegalArgumentException("a repeat gap is not negative, got: " + every);
        }
        if (repeat < 1) {
            throw new IllegalArgumentException("an action runs at least once, got: " + repeat);
        }
        if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("a chance is between zero and one, got: " + chance);
        }
    }

    /** Run once, at once, for everybody. This is what every line written before this feature carries. */
    public static ActionModifiers none() {
        return NONE;
    }

    /** Whether anything at all has to be checked or waited for. */
    public boolean isPlain() {
        return NONE.equals(this);
    }

    /**
     * The modifiers a brace block spells, and where the block ended.
     *
     * <p>The block is optional. A line with no brace after its verb reads as {@link #none()} and the payload
     * starts where it always did, so every file written before this keeps working untouched.
     *
     * @param after the payload as written, starting at the brace or at the payload itself
     * @throws IllegalArgumentException when a brace is opened and not closed, or a key is not one of the five
     */
    public static Parsed parse(String after) {
        Objects.requireNonNull(after, "after");
        String trimmed = after.strip();
        if (!trimmed.startsWith("{")) {
            return new Parsed(none(), trimmed);
        }
        int close = trimmed.indexOf('}');
        if (close < 0) {
            throw new IllegalArgumentException("an action modifier block is missing its closing '}': " + after);
        }
        return new Parsed(
                read(trimmed.substring(1, close)), trimmed.substring(close + 1).strip());
    }

    /** What a brace block parsed into, and the payload that followed it. */
    public record Parsed(ActionModifiers modifiers, String payload) {

        public Parsed {
            Objects.requireNonNull(modifiers, "modifiers");
            Objects.requireNonNull(payload, "payload");
        }
    }

    /**
     * One brace block's contents.
     *
     * <p>Split on commas, because a condition can hold a comparison but not a comma: {@code %level% >= 10} is
     * one part and {@code if=a, delay=20} is two. A condition that needed a comma would need quoting, and
     * quoting is a grammar an operator has to be taught rather than one they can guess.
     */
    private static ActionModifiers read(String block) {
        Duration delay = Duration.ZERO;
        int repeat = 1;
        Duration every = Duration.ZERO;
        Optional<String> condition = Optional.empty();
        double chance = 1;
        for (String part : block.split(",", -1)) {
            String written = part.strip();
            if (written.isEmpty()) {
                continue;
            }
            int equals = written.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("an action modifier is written as key=value, got: " + written);
            }
            String key = written.substring(0, equals).strip().toLowerCase(Locale.ROOT);
            String value = written.substring(equals + 1).strip();
            switch (key) {
                case "delay" -> delay = ticks(value, key);
                case "repeat" -> repeat = whole(value, key);
                case "every" -> every = ticks(value, key);
                case "if" -> condition = Optional.of(value);
                case "chance" -> chance = fraction(value);
                default ->
                    throw new IllegalArgumentException(
                            "an action modifier is one of delay, repeat, every, if or chance, got: " + key);
            }
        }
        return new ActionModifiers(delay, repeat, every, condition, chance);
    }

    private static Duration ticks(String value, String key) {
        return TICK.multipliedBy(whole(value, key));
    }

    private static int whole(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("the action modifier " + key + " is a whole number, got: " + value);
        }
    }

    private static double fraction(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("the action modifier chance is a number, got: " + value);
        }
    }
}
