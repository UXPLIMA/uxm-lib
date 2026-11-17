package com.uxplima.uxmlib.menu;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import com.uxplima.uxmlib.menu.runtime.MenuControl;
import com.uxplima.uxmlib.menu.spec.ListControlSyntax.SortDirection;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link MenuControl} a tap on a Bedrock form drives. A form is gone once tapped, so a repaint sends it again,
 * drawn from the menu's current state: a view whose state a tap changed (a keypad's entry, a toggle) stays on screen
 * and shows the change, as its chest does. However many repaints one tap asks for, the form is sent once.
 *
 * <p>A form keeps no list order, filter or search of its own, so those send it again as it is.
 */
@NullMarked
final class FormControl implements MenuControl {

    private final int page;
    private final IntConsumer resend;
    private final AtomicBoolean sent = new AtomicBoolean();

    /**
     * @param page the page the form showed
     * @param resend sends the form again at the page it is given
     */
    FormControl(int page, IntConsumer resend) {
        this.page = page;
        this.resend = Objects.requireNonNull(resend, "resend");
    }

    @Override
    public void refresh() {
        resendAt(page);
    }

    @Override
    public void refreshSlot(int slot) {
        resendAt(page);
    }

    @Override
    public void resetPagination() {
        resendAt(0);
    }

    @Override
    public void sortList(String listId, SortDirection direction) {
        resendAt(page);
    }

    @Override
    public void filterList(String listId, String key, String value) {
        resendAt(page);
    }

    @Override
    public void searchList(String listId, String key) {
        resendAt(page);
    }

    private void resendAt(int target) {
        if (sent.compareAndSet(false, true)) {
            resend.accept(target);
        }
    }
}
