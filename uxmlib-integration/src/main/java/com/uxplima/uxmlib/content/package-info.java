/**
 * The seams by which a plugin of ours attributes an action to somebody else's content.
 *
 * <p>Seven questions and one shape, and the shape is the one {@code claim} already proved: one interface per
 * question, a provider per vendor, and a composite that answers from whichever is installed. Never a
 * dependency, never a load order, and a server with none of them installed reads nothing and carries on.
 *
 * <p><strong>Every provider here is reflective.</strong> Not one of them names a vendor type in a signature
 * or a field, so not one vendor class loads on a server that does not run that vendor. That is a decision
 * and not a shortcut: fifteen {@code compileOnly} coordinates from a dozen repositories is a build that
 * breaks when any one of them is down, and this workspace has already been bitten by exactly that.
 *
 * <p>A vendor whose API changes shape therefore fails at run time rather than at compile time, and every
 * call site here treats that as "this plugin has nothing to say" rather than as an error. One vendor's
 * broken update costs its own answers and nothing else.
 */
@NullMarked
package com.uxplima.uxmlib.content;

import org.jspecify.annotations.NullMarked;
