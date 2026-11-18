package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.menu.spec.ListControlSyntax.SortDirection;
import org.junit.jupiter.api.Test;

/**
 * A tap on a Bedrock form that repaints its menu sends the form again, once however many repaints the tap asks for,
 * at the page it showed or the first page for a reset; a tap that repaints nothing sends nothing.
 */
class FormControlTest {

    private final List<Integer> sent = new ArrayList<>();

    @Test
    void aRepaintSendsTheFormAgainAtItsPageOnce() {
        FormControl control = new FormControl(2, sent::add);

        control.refresh();
        control.refreshSlot(4);
        control.refresh();

        assertThat(sent).containsExactly(2);
    }

    @Test
    void aResetSendsTheFirstPage() {
        new FormControl(3, sent::add).resetPagination();

        assertThat(sent).containsExactly(0);
    }

    @Test
    void aListOrderFilterOrSearchSendsTheFormAsItIs() {
        new FormControl(1, sent::add).sortList("rows", SortDirection.NEXT);
        new FormControl(1, sent::add).filterList("rows", "kind", "ore");
        new FormControl(1, sent::add).searchList("rows", "name");

        assertThat(sent).containsExactly(1, 1, 1);
    }

    @Test
    void aTapThatRepaintsNothingSendsNothing() {
        new FormControl(0, sent::add);

        assertThat(sent).isEmpty();
    }
}
