package com.goplay.core;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Emitter provides event-driven communication.
 * Supports on, off, once, emit operations similar to EventEmitter.
 */
public class Emitter {
    private Map<String, List<Listener>> callbacks = new HashMap<>();

    public static class Listener {
        private Function function;
        private Function originalFunction;

        public Listener(Function function) {
            this.function = function;
        }

        public Listener(Function function, Function original) {
            this.function = function;
            this.originalFunction = original;
        }
    }

    @FunctionalInterface
    public interface Function {
        void call(Object... args);
    }

    @FunctionalInterface
    public interface AsyncFunction {
        void call(Object... args) throws Exception;
    }

    /**
     * Register an event listener.
     */
    public Emitter on(String event, Function fn) {
        callbacks.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(new Listener(fn));
        return this;
    }

    /**
     * Register a one-time event listener.
     */
    public Emitter once(String event, Function fn) {
        Listener[] wrapper = new Listener[1];
        Function wrappedFn = (args) -> {
            off(event, wrapper[0].function);
            fn.call(args);
        };
        wrapper[0] = new Listener(wrappedFn, fn);
        callbacks.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(wrapper[0]);
        return this;
    }

    /**
     * Remove an event listener.
     */
    public Emitter off(String event, Function fn) {
        List<Listener> listeners = callbacks.get(event);
        if (listeners == null) {
            return this;
        }

        listeners.removeIf(cb ->
                cb.function == fn || cb.originalFunction == fn);

        if (listeners.isEmpty()) {
            callbacks.remove(event);
        }
        return this;
    }

    /**
     * Remove all listeners for an event or all events.
     */
    public Emitter off(String event) {
        callbacks.remove(event);
        return this;
    }

    /**
     * Remove all listeners.
     */
    public Emitter removeAllListeners() {
        callbacks.clear();
        return this;
    }

    /**
     * Emit an event synchronously.
     */
    public Emitter emit(String event, Object... args) {
        List<Listener> listeners = callbacks.get(event);
        if (listeners != null) {
            for (Listener listener : listeners) {
                listener.function.call(args);
            }
        }
        return this;
    }

    /**
     * Emit an event asynchronously.
     */
    public Emitter emitAsync(String event, Object... args) throws Exception {
        List<Listener> listeners = callbacks.get(event);
        if (listeners != null) {
            for (Listener listener : listeners) {
                listener.function.call(args);
            }
        }
        return this;
    }

    /**
     * Get all listeners for an event.
     */
    public List<Listener> listeners(String event) {
        return callbacks.getOrDefault(event, Collections.emptyList());
    }

    /**
     * Check if event has listeners.
     */
    public boolean hasListeners(String event) {
        return !listeners(event).isEmpty();
    }

    public Map<String, List<Listener>> getCallbacks() {
        return callbacks;
    }
}
