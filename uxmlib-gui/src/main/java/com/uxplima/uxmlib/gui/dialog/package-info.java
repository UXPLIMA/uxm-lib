/**
 * A small fluent facade over Paper's server-side {@code Dialog} screens (the native dialog UI added in
 * 1.21.6). {@link com.uxplima.uxmlib.gui.dialog.DialogScreen} builds a notice (one acknowledge button) or a
 * confirmation (yes / no) with a title, body text, and buttons that map to a server-side callback, then
 * shows it to a player; {@link com.uxplima.uxmlib.gui.dialog.DialogInputScreen} is its text-input sibling,
 * a dialog carrying one text field whose typed line is delivered to a callback on submit. The whole feature
 * is version-gated: on a server older than 1.21.6 the Dialog API is absent, so
 * {@link com.uxplima.uxmlib.gui.dialog.DialogScreen#isSupported()} reports false and {@code show} is a no-op
 * rather than a crash, and {@code DialogInputScreen} runs its cancel callback instead of prompting.
 */
@NullMarked
package com.uxplima.uxmlib.gui.dialog;

import org.jspecify.annotations.NullMarked;
