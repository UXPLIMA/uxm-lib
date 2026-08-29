/**
 * The look of a plugin, kept out of its code: a palette of named roles ({@link
 * com.uxplima.uxmlib.text.style.Theme}), the letters a template is set in ({@link
 * com.uxplima.uxmlib.text.style.Typography}), and the tokens a message file writes instead of colours
 * ({@link com.uxplima.uxmlib.text.style.StyleTokens}). {@link com.uxplima.uxmlib.text.style.Styler} applies
 * the pass to a whole {@link com.uxplima.uxmlib.text.message.MessageCatalog} once, at load.
 *
 * <p>The mechanism is here; the values — which hex an accent is, which glyph a bullet is, which languages are
 * written in small capitals — belong in a file an operator owns.
 */
@NullMarked
package com.uxplima.uxmlib.text.style;

import org.jspecify.annotations.NullMarked;
