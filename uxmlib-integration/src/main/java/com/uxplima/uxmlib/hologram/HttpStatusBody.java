package com.uxplima.uxmlib.hologram;

import java.util.Objects;

/**
 * The minimal slice of an HTTP response {@link MojangSkinResolver} needs: the status code and the body
 * text. Keeping the resolver's transport seam this small (instead of a full {@code HttpResponse}) lets a
 * test script responses without faking the whole JDK response surface.
 */
record HttpStatusBody(int status, String body) {

    HttpStatusBody {
        Objects.requireNonNull(body, "body");
    }
}
