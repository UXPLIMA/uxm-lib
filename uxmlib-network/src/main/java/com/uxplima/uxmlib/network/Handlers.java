package com.uxplima.uxmlib.network;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** The handlers of every topic, and the one place a handler's failure is contained. */
final class Handlers {

    private final Map<String, List<Consumer<Frame>>> byTopic = new ConcurrentHashMap<>();
    private final System.Logger log;

    Handlers(System.Logger log) {
        this.log = log;
    }

    void add(String topic, Consumer<Frame> handler) {
        byTopic.computeIfAbsent(topic, unused -> new CopyOnWriteArrayList<>()).add(handler);
    }

    void deliver(Frame frame) {
        for (Consumer<Frame> handler : byTopic.getOrDefault(frame.topic(), List.of())) {
            try {
                handler.accept(frame);
            } catch (RuntimeException failure) {
                // One plugin's broken handler must not stop the frames of every other topic.
                log.log(System.Logger.Level.WARNING, "A handler of the topic " + frame.topic() + " failed.", failure);
            }
        }
    }
}
