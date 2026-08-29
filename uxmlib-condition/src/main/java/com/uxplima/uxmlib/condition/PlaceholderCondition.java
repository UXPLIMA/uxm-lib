package com.uxplima.uxmlib.condition;

import java.util.Objects;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * The generic placeholder-comparator condition: two operand <em>templates</em> compared under a {@link
 * Comparison}. At test time both templates are resolved through the request's injected {@link
 * OperandResolver} (so {@code "%player_health%"} becomes a concrete value, and a literal like {@code "10"}
 * resolves to itself), then the comparison is applied under the documented numeric-or-string rules.
 *
 * <p>This single type covers an unbounded set of config-only checks: {@code %player_health% >= 10},
 * {@code %vault_eco_balance% > 100}, {@code %player_world% == world_nether}, without any per-check code.
 * It is the highest-leverage condition and is meant to ship first.
 */
public final class PlaceholderCondition implements Condition {

    private final String leftTemplate;
    private final Comparison comparison;
    private final String rightTemplate;

    private PlaceholderCondition(String leftTemplate, Comparison comparison, String rightTemplate) {
        this.leftTemplate = leftTemplate;
        this.comparison = comparison;
        this.rightTemplate = rightTemplate;
    }

    /** A condition comparing two operand templates under the given operator. */
    public static PlaceholderCondition of(String leftTemplate, Operator operator, String rightTemplate) {
        Objects.requireNonNull(leftTemplate, "leftTemplate");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(rightTemplate, "rightTemplate");
        return new PlaceholderCondition(leftTemplate, Comparison.of(operator), rightTemplate);
    }

    /**
     * Parse a whole {@code left <op> right} expression (e.g. {@code "%player_health% >= 10"}) into a
     * condition. The two sides are kept verbatim as templates and resolved per request; only the operator is
     * fixed now. Throws {@link IllegalArgumentException} if no known operator appears.
     */
    public static PlaceholderCondition parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        Comparison.ParsedComparison parsed = Comparison.parse(expression);
        return new PlaceholderCondition(parsed.left(), parsed.comparison(), parsed.right());
    }

    /** The unresolved left operand template. */
    public String leftTemplate() {
        return leftTemplate;
    }

    /** The unresolved right operand template. */
    public String rightTemplate() {
        return rightTemplate;
    }

    /** The operator this condition compares under. */
    public Operator operator() {
        return comparison.operator();
    }

    @Override
    public boolean test(ConditionRequest request) {
        Objects.requireNonNull(request, "request");
        @Nullable Player player = request.player().orElse(null);
        OperandResolver resolver = request.resolver();
        String left = resolver.resolve(player, leftTemplate);
        String right = resolver.resolve(player, rightTemplate);
        return comparison.test(left, right);
    }
}
