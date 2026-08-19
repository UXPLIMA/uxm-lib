package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

class UpdateMessagesTest {

    private static final Release RELEASE = new Release("1.5.0", "https://github.com/o/r/releases/latest");

    @Test
    void notificationMentionsBothVersionsAsPlainText() {
        Component message = UpdateMessages.notification("uxmLib", "1.4.0", RELEASE);
        String plain = Text.plain(message);
        assertThat(plain).contains("uxmLib", "1.4.0", "1.5.0");
    }

    @Test
    void notificationCarriesAClickableOpenUrlToTheRelease() {
        Component message = UpdateMessages.notification("uxmLib", "1.4.0", RELEASE);
        assertThat(hasClick(message, ClickEvent.openUrl(RELEASE.url()))).isTrue();
    }

    /**
     * Whether any component in the tree carries exactly {@code expected} as its click event. Adventure 5
     * removed {@code ClickEvent#value()} in favour of a typed payload, so reading the URL back off the event
     * only compiles against one line at a time; comparing whole events works on both. {@code Object} because a
     * declared {@code ClickEvent} would be a raw type on 5.x, where the class gained a type parameter.
     */
    private static boolean hasClick(Component component, Object expected) {
        if (expected.equals(component.clickEvent())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasClick(child, expected)) {
                return true;
            }
        }
        return false;
    }
}
