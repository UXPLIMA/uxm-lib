/**
 * The currencies a plugin prices in, read from a {@code currencies.conf} an operator writes.
 *
 * <p>{@link com.uxplima.uxmlib.condition.currency.CurrencyFile} reads the file into descriptions with no
 * server running, {@link com.uxplima.uxmlib.condition.currency.CurrencyBuilder} turns them into live
 * currencies, {@link com.uxplima.uxmlib.condition.currency.CurrencyRegistry} holds them in the operator's
 * order, {@link com.uxplima.uxmlib.condition.currency.CurrencyWallet} answers the action engine's money
 * questions from them, and {@link com.uxplima.uxmlib.condition.currency.MoneyText} writes an amount the way
 * the file asks.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.condition.currency;
