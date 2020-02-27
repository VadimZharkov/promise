package com.vzharkov.promise;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A Promise represents the result of an delayed computation.
 *
 * @param <T> Result type
 */
public class Promise<T> {
    private enum State {
        PENDING,
        FULFILLED,
        REJECTED
    }

    /**
     * Nonblocking concurrent stack using Treiber's algorithm.
     */
    private static class Stack<V> {
        private static class Item<V> {
            final V value;
            final Item<V> next;

            Item(final V value, final Item<V> next) {
                this.value = value;
                this.next = next;
            }
        }

        private AtomicReference<Item<V>> top = new AtomicReference<>();

        void push(final V value) {
            top.updateAndGet(prev -> new Item<>(value, prev));
        }

        V pop() {
            Item<V> prev, next;
            do {
                prev = top.get();
                if (prev == null)
                    return null;
                next = prev.next;
            } while (!top.compareAndSet(prev, next));

            return prev.value;
        }
    }

    @FunctionalInterface
    public interface ThrowingBiConsumer<T, U> extends BiConsumer<T, U> {
        @Override
        default void accept(T t, U u) {
            try {
                accept0(t,u);
            } catch (Throwable ex) {
                sneakyThrow(ex);
            }
        }

        void accept0(T t, U u) throws Throwable;

        @SuppressWarnings("unchecked")
        static <E extends Throwable> void sneakyThrow(Throwable ex) throws E {
            throw (E) ex;
        }
    }

    private volatile Object result;
    private volatile State state = State.PENDING;
    private final Stack<Consumer<T>> completions = new Stack<>();
    private final Stack<Consumer<? super Throwable>> catchers = new Stack<>();
    private final Object lock = new Object();

    public static <T> Promise<T> of(final ThrowingBiConsumer<Consumer<T>, Consumer<? super Throwable>> consumer) {
        Objects.requireNonNull(consumer, "consumer is null");

        final Promise<T> promise = new Promise<>();
        try {
            consumer.accept(promise::completeWithValue, promise::completeWithError);
            return promise;
        } catch (Throwable ex) {
            promise.completeWithError(ex);
            return promise;
        }
    }

    public boolean isCompleted() {
        return state != State.PENDING;
    }

    @SuppressWarnings("unchecked")
    public Promise<T> andThan(final Consumer<T> callback) {
        Objects.requireNonNull(callback, "callback is null");

        if (state == State.FULFILLED) {
            callback.accept((T) result);
        } else {
            completions.push(callback);
        }
        return this;
    }

    public Promise<T> catchError(final Consumer<? super Throwable> callback) {
        Objects.requireNonNull(callback, "callback is null");

        if (state == State.REJECTED) {
            callback.accept((Throwable) result);
        } else {
            catchers.push(callback);
        }
        return this;
    }

    protected void completeWithValue(final T value) {
        boolean completed = false;
        if (!isCompleted()) {
            synchronized(lock) {
                if (!isCompleted()) {
                    this.result = value;
                    this.state= State.FULFILLED;
                    completed = true;
                }
            }
        }
        if (completed) {
            Consumer<T> completion;
            while ((completion = completions.pop()) != null) {
                completion.accept(value);
            }
        } else {
            throw new IllegalStateException();
        }
    }

    protected void completeWithError(final Throwable error) {
        Objects.requireNonNull(error);

        boolean completed = false;
        if (!isCompleted()) {
            synchronized(lock) {
                if (!isCompleted()) {
                    this.result = error;
                    this.state= State.REJECTED;
                    completed = true;
                }
            }
        }
        if (completed) {
            Consumer<? super Throwable> catcher;
            while ((catcher = catchers.pop()) != null) {
                catcher.accept(error);
            }
        } else {
            throw new IllegalStateException();
        }
    }
}
