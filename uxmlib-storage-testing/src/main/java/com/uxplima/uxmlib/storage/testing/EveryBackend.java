package com.uxplima.uxmlib.storage.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Run a test once on each backend, handing it a {@link RealDatabase} that is empty and its own.
 *
 * <p>The test carries the tag {@value RealDatabases#TAG}, so a build can run these in a task of their own: they
 * need a container runtime and take seconds rather than milliseconds.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ParameterizedTest(name = "{0}")
@ArgumentsSource(RealDatabases.class)
@Tag(RealDatabases.TAG)
public @interface EveryBackend {}
