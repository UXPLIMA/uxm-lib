package com.uxplima.uxmlib.packet.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScoreboardPacketEventTest {

    @Test
    void displayEventsDistinguishOwnershipFromClears() {
        ScoreboardPacketEvent.Display shown =
                new ScoreboardPacketEvent.Display(ScoreboardDisplaySlot.SIDEBAR, "foreign");
        ScoreboardPacketEvent.Display cleared = new ScoreboardPacketEvent.Display(ScoreboardDisplaySlot.SIDEBAR, null);

        assertThat(shown.objectiveName()).hasValue("foreign");
        assertThat(cleared.objectiveName()).isEmpty();
    }

    @Test
    void objectiveEventsExposeLifecycleAction() {
        ScoreboardPacketEvent.Objective removed =
                new ScoreboardPacketEvent.Objective("foreign", ScoreboardObjectiveAction.REMOVE);

        assertThat(removed.objectiveName()).isEqualTo("foreign");
        assertThat(removed.action()).isEqualTo(ScoreboardObjectiveAction.REMOVE);
    }
}
