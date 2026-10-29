/**
 * The one place in the currency layer that names a Treasury type.
 *
 * <p>Every other economy is reached by a description an operator writes in {@code currencies.conf}, and
 * the reader that follows it names nothing. Treasury cannot be described that way: it answers through a
 * subscriber rather than returning, and a balance is two hops away. So it is written out, and only
 * this package names {@code me.lokka30..}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.condition.currency.treasury;
