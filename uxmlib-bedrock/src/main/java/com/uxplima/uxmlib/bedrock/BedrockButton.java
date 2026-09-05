package com.uxplima.uxmlib.bedrock;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * One Bedrock SimpleForm button: its label plus an optional icon. A caller builds these from the entries it
 * wants to show and hands them to the {@link BedrockScreen}, which turns each into a Cumulus button: with the
 * icon when present, a plain text button when {@code image} is {@code null}. Kept SDK-free so a caller never
 * names Cumulus.
 *
 * @param text the button label as plain text; never {@code null}
 * @param image the button's icon, or {@code null} for a text-only button
 */
public record BedrockButton(String text, @Nullable BedrockImage image) {

    public BedrockButton {
        Objects.requireNonNull(text, "text");
    }
}
