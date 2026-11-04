package com.uxplima.uxmlib.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;

import org.junit.jupiter.api.Test;

/** The choice of chests over forms reads the same for every plugin, on the player's thread and off it. */
class BedrockMenuChoiceTest {

    private final Map<NamespacedKey, Object> data = new HashMap<>();

    private final PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {PersistentDataContainer.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "set" -> data.put((NamespacedKey) args[0], args[2]);
                case "get" -> data.get((NamespacedKey) args[0]);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });

    private final Player alex = (Player) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> container;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });

    private boolean onOwner = true;

    private final List<Runnable> waiting = new ArrayList<>();

    private final BedrockMenuChoice choice =
            BedrockMenuChoice.with(player -> onOwner, (player, work) -> waiting.add(work));

    @Test
    void nothingStoredMeansForms() {
        assertThat(choice.wantsChest(alex)).isFalse();
    }

    @Test
    void aChoiceWrittenOnThePlayersThreadIsReadByAnotherCopy() {
        choice.write(alex, true);

        BedrockMenuChoice another = BedrockMenuChoice.with(player -> true, (player, work) -> work.run());
        assertThat(another.wantsChest(alex)).isTrue();
        assertThat(data).containsEntry(BedrockMenuChoice.KEY, "chest");
    }

    @Test
    void offThePlayersThreadAWriteWaitsAndAReadUsesTheLastAnswer() {
        onOwner = false;

        choice.write(alex, true);

        assertThat(data).isEmpty();
        assertThat(choice.wantsChest(alex)).isTrue();
        waiting.forEach(Runnable::run);
        assertThat(data).containsEntry(BedrockMenuChoice.KEY, "chest");
    }
}
