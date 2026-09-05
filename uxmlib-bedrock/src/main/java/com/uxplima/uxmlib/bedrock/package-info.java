/**
 * Native Bedrock forms for a server that lets Bedrock clients in through Geyser or Floodgate.
 *
 * <p>A Bedrock client cannot see a chest menu the way a Java client does. It has its own window vocabulary, so
 * a plugin that draws a menu has two questions to answer: is this viewer a Bedrock player, and how do I send
 * them a form? {@link com.uxplima.uxmlib.bedrock.BedrockDetector} answers the first and
 * {@link com.uxplima.uxmlib.bedrock.BedrockScreen} answers the second.
 *
 * <p>Both follow one shape. Each is an interface with a {@code NONE} constant that names no SDK type at all,
 * and a {@code forServer(Server)} factory that returns a backed implementation only when the plugin behind it
 * is enabled. The backed implementations are package-private, so the {@code org.geysermc} classes they
 * reference are named in exactly two files and reached only inside the enabled branch. On a Java-only server
 * the factories answer {@code NONE} and no Geyser or Floodgate class is ever loaded. That property is why the
 * factory exists: a caller must never have to guard the call itself.
 *
 * <p>The rest of the package is the SDK-free vocabulary a caller builds a form out of:
 * {@link com.uxplima.uxmlib.bedrock.BedrockButton} and {@link com.uxplima.uxmlib.bedrock.BedrockImage} for a
 * SimpleForm, {@link com.uxplima.uxmlib.bedrock.BedrockWidget} for a CustomForm, and
 * {@link com.uxplima.uxmlib.bedrock.BedrockIcons} to turn a material name or a head reference into a button
 * icon. None of them names a Cumulus type, so a caller can build and test a form with no SDK on the classpath.
 */
@NullMarked
package com.uxplima.uxmlib.bedrock;

import org.jspecify.annotations.NullMarked;
