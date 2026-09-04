/**
 * The language of a player: which language files a plugin has, which language a viewer reads, and where that
 * answer is kept.
 *
 * <p>{@link com.uxplima.uxmlib.text.language.LanguageFiles} finds the files rather than listing them, so a
 * language exists because an operator wrote a file and never because a plugin was rebuilt.
 * {@link com.uxplima.uxmlib.text.language.LanguageResolver} answers for a viewer in a fixed order: the
 * language the server forces, the player's own choice, the client language, then the configured default.
 * {@link com.uxplima.uxmlib.text.language.PlayerLanguages} keeps the choice, and
 * {@link com.uxplima.uxmlib.text.language.LanguageService} is the seam a network-wide provider registers
 * itself through. A plugin that has neither still resolves a language, which is the point: the mechanism
 * needs no other plugin.
 */
@NullMarked
package com.uxplima.uxmlib.text.language;

import org.jspecify.annotations.NullMarked;
