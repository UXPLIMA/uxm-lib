package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a command on a permission node. On a {@link Subcommand} method it guards that branch; on a
 * {@link Command} class it guards every branch. The node becomes a Brigadier {@code requires} check.
 *
 * <p><strong>This hides, it does not refuse.</strong> A sender without the node never sees the command: it is
 * absent from tab-completion and unknown to the dispatcher, so no "you lack permission" message can ever
 * fire for it — a key wired to one would be dead text. That is the right shape for a staff command whose
 * existence is not public. When the command should instead announce that it refused, leave {@code @Permission}
 * off and register a {@link com.uxplima.uxmlib.command.annotation.CommandCondition} that tests the permission
 * and throws with the reason; conditions run inside the executor, where a reply is still possible.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Permission {

    /** The permission node the sender must hold. */
    String value();
}
