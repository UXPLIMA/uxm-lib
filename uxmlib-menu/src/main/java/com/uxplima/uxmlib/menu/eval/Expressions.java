package com.uxplima.uxmlib.menu.eval;

import java.util.Map;
import java.util.Objects;

/**
 * The public entry point to the sandboxed expression evaluator. The caller substitutes any {@code %placeholder%}
 * to a literal before calling, so what arrives here is a self-contained arithmetic/boolean expression with no
 * outside references: that keeps this layer pure (no Bukkit, no I/O, no reflection) and trivially unit-testable.
 * The grammar is the whole surface: numbers, the four arithmetic operators plus {@code ^} and {@code %}, the
 * allow-listed functions, comparisons, and boolean logic. Anything else: an unknown identifier, a method-like
 * token, oversized or over-nested input: is rejected with an {@link ExpressionException}, never executed.
 */
public final class Expressions {

    /** Hard ceiling on input length; a longer string is rejected before lexing, bounding parser work. */
    private static final int MAX_LENGTH = 1024;

    private Expressions() {}

    /**
     * Evaluate with named numbers in scope, so an expression can be written about something.
     *
     * <p>The sandbox knew functions and literals and nothing else, which is enough for a menu deciding a page
     * count and not enough for a file describing a motion: a circle is written about the turn it is at. The
     * <p>A name is resolved as a token and never substituted into the text first. Substitution looks simpler
     * and is wrong: replacing {@code t} with its value in {@code cos(t * tau())} rewrites the {@code t} of
     * {@code tau} too, and the expression stops parsing. It failed on the first circle anybody wrote.
     */
    public static double evaluateNumber(String expression, Map<String, Double> named) throws ExpressionException {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(named, "named");
        if (expression.length() > MAX_LENGTH) {
            throw new ExpressionException("expression exceeds the maximum length of " + MAX_LENGTH);
        }
        Object value = new Parser(new Lexer(expression).tokenize(), named).parse();
        if (value instanceof Double number) {
            return number;
        }
        throw new ExpressionException("expression did not evaluate to a number");
    }

    /** Evaluate {@code expression} to a finite number, or throw if it is malformed or does not yield a number. */
    public static double evaluateNumber(String expression) throws ExpressionException {
        Object value = evaluate(expression);
        if (value instanceof Double d) {
            return d;
        }
        throw new ExpressionException("expression did not evaluate to a number");
    }

    /** Evaluate {@code expression} to a boolean, or throw if it is malformed or does not yield a boolean. */
    public static boolean evaluateBoolean(String expression) throws ExpressionException {
        Object value = evaluate(expression);
        if (value instanceof Boolean b) {
            return b;
        }
        throw new ExpressionException("expression did not evaluate to a boolean");
    }

    /**
     * Render {@code value} the way the evaluator renders a number back into text: an integral value without a
     * trailing {@code .0}. A caller that substitutes an evaluated number into rendered text (a {@code {math: …}}
     * placeholder, a {@code data_number_*} reader) reuses this so its output matches the number formatting the rest
     * of the evaluator produces, rather than re-deriving it and drifting.
     */
    public static String format(double value) {
        return Values.format(value);
    }

    private static Object evaluate(String expression) throws ExpressionException {
        Objects.requireNonNull(expression, "expression");
        if (expression.length() > MAX_LENGTH) {
            throw new ExpressionException("expression exceeds the maximum length of " + MAX_LENGTH);
        }
        return new Parser(new Lexer(expression).tokenize()).parse();
    }
}
