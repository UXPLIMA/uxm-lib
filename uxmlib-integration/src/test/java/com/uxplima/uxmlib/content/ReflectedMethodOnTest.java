package com.uxplima.uxmlib.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A method looked up on an object's own class is found once per class. A claim check runs on every block
 * break, and {@link Class#getMethod} searches the class and copies the method on every call.
 */
class ReflectedMethodOnTest {

    @Test
    void theSameMethodIsHandedBackForTheSameClass() throws Exception {
        Method first = Reflected.methodOn(new ArrayList<>(), "size");
        Method second = Reflected.methodOn(new ArrayList<>(List.of("x")), "size");

        assertThat(second).isSameAs(first);
        assertThat(first.invoke(new ArrayList<>(List.of("a", "b")))).isEqualTo(2);
    }

    @Test
    void parametersTellTwoOverloadsApart() throws Exception {
        Method byIndex = Reflected.methodOn(new ArrayList<>(), "remove", int.class);
        Method byValue = Reflected.methodOn(new ArrayList<>(), "remove", Object.class);

        assertThat(byIndex.getParameterTypes()).containsExactly(int.class);
        assertThat(byValue.getParameterTypes()).containsExactly(Object.class);
    }

    @Test
    void theMethodsOfAClassAreListedOnce() {
        List<Method> first = Reflected.methods(ArrayList.class);

        assertThat(Reflected.methods(ArrayList.class)).isSameAs(first);
        assertThat(first).extracting(Method::getName).contains("size", "add");
    }

    @Test
    void aRequiredTypeIsFoundOnceAndAMissingOneThrows() throws Exception {
        assertThat(Reflected.requireType("java.util.ArrayList")).isSameAs(ArrayList.class);
        assertThatExceptionOfType(ClassNotFoundException.class).isThrownBy(() -> Reflected.requireType("no.such.Type"));
    }

    @Test
    void aMissingMethodThrowsAsGetMethodDoes() {
        assertThatExceptionOfType(NoSuchMethodException.class)
                .isThrownBy(() -> Reflected.methodOn(new ArrayList<>(), "noSuchMethod"));
    }
}
