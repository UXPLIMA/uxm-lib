package com.uxplima.uxmlib.condition.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * An amount of money, written the way the operator asked for.
 *
 * <p>One rule is not the operator's, and it is here rather than in the file. A number that has more
 * places than the display allows is rounded up. A price shown must never be under the price charged: a
 * buyer who reads ten and pays ten and a hundredth has been lied to, and it is the one rounding a player
 * can see. The plugin may show a hundredth more than it takes; it may never show a hundredth less.
 */
public final class MoneyText {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /** The one number a currency may have a second shape for. */
    private final Map<String, CurrencyDisplay> byCurrency;

    private final CurrencyDisplay fallback;

    public MoneyText(Map<String, CurrencyDisplay> byCurrency, CurrencyDisplay fallback) {
        this.byCurrency = Map.copyOf(Objects.requireNonNull(byCurrency, "byCurrency"));
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /** The {@code display} block of every currency in {@code currencies.conf}. */
    public static MoneyText read(HoconConfig config) {
        Objects.requireNonNull(config, "config");
        Map<String, CurrencyDisplay> read = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                config.nodeAt("currencies").childrenMap().entrySet()) {
            // Keyed the way CurrencyId writes an id, lower case, so "Coins" finds its display block.
            read.put(
                    String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT),
                    display(entry.getValue().node("display")));
        }
        return new MoneyText(read, CurrencyDisplay.plain());
    }

    /**
     * What a player reads for this amount.
     *
     * <p>An amount that is written as one takes the {@code format-one} of its currency, so a plugin that
     * prices in diamonds says one diamond and not one diamonds. A file that leaves that line out writes
     * every number the same way.
     */
    public String of(Money money) {
        Objects.requireNonNull(money, "money");
        CurrencyDisplay display = byCurrency.getOrDefault(money.currency().value(), fallback);
        String written = number(money.amount(), display);
        String shape = money.amount().compareTo(BigDecimal.ONE) == 0 ? display.formatOne() : display.format();
        return shape.replace("{amount}", written)
                .replace("{currency}", money.currency().value());
    }

    private static String number(BigDecimal amount, CurrencyDisplay display) {
        String sign = amount.signum() < 0 ? "-" : "";
        BigDecimal size = amount.abs();
        if (display.shortForm() && !display.suffixes().isEmpty() && size.compareTo(THOUSAND) >= 0) {
            int step = 0;
            BigDecimal left = size;
            while (step < display.suffixes().size() && left.compareTo(THOUSAND) >= 0) {
                left = left.divide(THOUSAND, 4, RoundingMode.DOWN);
                step++;
            }
            BigDecimal shown = size.divide(THOUSAND.pow(step), 1, RoundingMode.UP);
            // 999,999 rounds up to 1000.0k; it reads as 1M when there is a next suffix.
            if (shown.compareTo(THOUSAND) >= 0 && step < display.suffixes().size()) {
                step++;
                shown = size.divide(THOUSAND.pow(step), 1, RoundingMode.UP);
            }
            return sign + plain(shown, display) + display.suffixes().get(step - 1);
        }
        return sign + plain(size.setScale(display.decimals(), RoundingMode.UP), display);
    }

    /** The digits, grouped and pointed as the file asks. */
    private static String plain(BigDecimal amount, CurrencyDisplay display) {
        BigDecimal shown = amount;
        if (display.trim()) {
            shown = shown.stripTrailingZeros();
        }
        if (shown.scale() < 0) {
            shown = shown.setScale(0);
        }
        String written = shown.toPlainString();
        int at = written.indexOf('.');
        String whole = at < 0 ? written : written.substring(0, at);
        String fraction = at < 0 ? "" : written.substring(at + 1);
        String grouped = group(whole, display.thousands());
        return fraction.isEmpty() ? grouped : grouped + display.point() + fraction;
    }

    private static String group(String digits, String separator) {
        if (separator.isEmpty() || digits.length() <= 3) {
            return digits;
        }
        StringBuilder built = new StringBuilder(digits.length() + digits.length() / 3);
        int lead = digits.length() % 3;
        if (lead > 0) {
            built.append(digits, 0, lead);
        }
        for (int at = lead; at < digits.length(); at += 3) {
            if (built.length() > 0) {
                built.append(separator);
            }
            built.append(digits, at, at + 3);
        }
        return built.toString();
    }

    private static CurrencyDisplay display(ConfigurationNode node) {
        CurrencyDisplay plain = CurrencyDisplay.plain();
        List<String> suffixes = suffixes(node.node("short-suffixes"), plain.suffixes());
        String format = text(node, "format", plain.format());
        return new CurrencyDisplay(
                format,
                text(node, "format-one", format),
                node.node("decimals").getInt(plain.decimals()),
                node.node("trim").getBoolean(plain.trim()),
                text(node, "thousands", plain.thousands()),
                text(node, "point", plain.point()),
                node.node("short").getBoolean(plain.shortForm()),
                suffixes);
    }

    private static List<String> suffixes(ConfigurationNode node, List<String> fallback) {
        if (node.virtual() || node.childrenList().isEmpty()) {
            return fallback;
        }
        List<String> read = new java.util.ArrayList<>();
        for (ConfigurationNode written : node.childrenList()) {
            @Nullable String one = written.getString();
            if (one != null) {
                read.add(one);
            }
        }
        return read.isEmpty() ? fallback : List.copyOf(read);
    }

    private static String text(ConfigurationNode node, String key, String fallback) {
        return Objects.requireNonNullElse(node.node(key).getString(fallback), fallback);
    }
}
