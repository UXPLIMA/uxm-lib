package com.uxplima.uxmlib.menu.property;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The catalog keys a {@link ListProperty}'s sub-menu raises: the menu title, the per-entry name (with an
 * {@code {entry}} placeholder) and its action-hint lore, the add button name and its anvil prompt, the edit
 * anvil prompt (also {@code {entry}}), the remove confirm title, and the back button name. Bundling them keeps
 * the {@link ListProperty} constructor short and lets a module declare its list-editor wording in one place,
 * every string still resolving through the message catalog (never an inline literal).
 *
 * @param title the sub-menu title
 * @param entryName the per-entry button name; carries an {@code {entry}} placeholder
 * @param entryHints the per-entry lore describing the click actions (edit / move / remove)
 * @param addName the add button name
 * @param addPrompt the anvil hint shown when adding a new entry
 * @param editPrompt the anvil hint shown when editing an entry; carries an {@code {entry}} placeholder
 * @param removeConfirm the confirm-menu title shown before removing an entry
 * @param backName the back button name
 * @param emptyValue the word the button shows when the list holds nothing; empty to show the count instead
 * @param actionLine the {@code →} line of the button that opens this list; empty to take the editor's
 */
@NullMarked
public record ListPropertyText(
        String title,
        String entryName,
        String entryHints,
        String addName,
        String addPrompt,
        String editPrompt,
        String removeConfirm,
        String backName,
        String emptyValue,
        String actionLine) {

    /**
     * The eight keys a list editor needed before it had a word for an empty list or a click line of its own.
     *
     * <p>Kept so every caller that names them compiles unchanged and keeps the button it had: the count, and
     * the editor's own click line. A caller that wants the button to read as a value rather than as a number
     * names the two extra keys.
     */
    public ListPropertyText(
            String title,
            String entryName,
            String entryHints,
            String addName,
            String addPrompt,
            String editPrompt,
            String removeConfirm,
            String backName) {
        this(title, entryName, entryHints, addName, addPrompt, editPrompt, removeConfirm, backName, "", "");
    }

    public ListPropertyText {
        Objects.requireNonNull(emptyValue, "emptyValue");
        Objects.requireNonNull(actionLine, "actionLine");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(entryHints, "entryHints");
        Objects.requireNonNull(addName, "addName");
        Objects.requireNonNull(addPrompt, "addPrompt");
        Objects.requireNonNull(editPrompt, "editPrompt");
        Objects.requireNonNull(removeConfirm, "removeConfirm");
        Objects.requireNonNull(backName, "backName");
    }
}
