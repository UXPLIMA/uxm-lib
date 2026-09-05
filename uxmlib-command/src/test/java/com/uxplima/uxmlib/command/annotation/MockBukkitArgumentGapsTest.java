package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

/**
 * Pins the MockBukkit gaps that four tests in this package are {@code @Disabled} for.
 *
 * <p>A reason written on a disabled test is a claim about the world at the moment somebody wrote it, and
 * nothing normally fails when it stops being true: MockBukkit implements the method, the reason becomes
 * false, and the test stays switched off with a justification that no longer holds. {@code
 * verifyNoAbortedTests} cannot see it either, because a disabled test never runs and so never aborts.
 *
 * <p>So the claim is asked rather than asserted in prose. Each of these fails the day the mock grows the
 * method, and a failure here means the matching test can be re-enabled, not that anything is broken.
 */
class MockBukkitArgumentGapsTest {

    @Test
    void finePositionIsStillUnimplemented() {
        assertThatThrownBy(ArgumentTypes::finePosition).isInstanceOf(UnimplementedOperationException.class);
    }

    @Test
    void worldIsStillUnimplemented() {
        assertThatThrownBy(ArgumentTypes::world).isInstanceOf(UnimplementedOperationException.class);
    }

    @Test
    void uuidIsStillUnimplemented() {
        assertThatThrownBy(ArgumentTypes::uuid).isInstanceOf(UnimplementedOperationException.class);
    }

    @Test
    void resourceIsStillUnimplemented() {
        assertThatThrownBy(() -> ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                .isInstanceOf(UnimplementedOperationException.class);
        assertThatThrownBy(() -> ArgumentTypes.resource(RegistryKey.ITEM))
                .isInstanceOf(UnimplementedOperationException.class);
    }
}
