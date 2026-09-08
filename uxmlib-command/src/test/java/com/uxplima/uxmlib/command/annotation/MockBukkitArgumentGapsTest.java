package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

/**
 * Pins the MockBukkit gaps that keep five assertions in this package weaker than they could be.
 *
 * <p>Those five used to be {@code @Disabled} with the gap written in the annotation. A disabled test protects
 * nothing and reports as green, and no rule catches it: {@code verifyNoAbortedTests} cannot see a test that
 * never runs and so never aborts. They ask the runnable half now, which is that the type has a resolver and
 * the resolver calls itself native. What they cannot ask is what {@code argumentType()} returns, because
 * calling it is exactly the thing the mock does not implement.
 *
 * <p>So the gap is asked here rather than asserted in prose. Each of these fails the day the mock grows the
 * method, and a failure means {@code aLocationIsResolvedByANativeArgument} and its four siblings can go back
 * to building the whole node, not that anything is broken.
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
