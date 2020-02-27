package com.vzharkov.promise;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class PromiseTest {

    @Test
    void promiseGotTheResult() {
        int[] holder = new int[] { 0 };

        Promise<Integer> promise = Promise.of((resolve, reject) -> resolve.accept(10));
        promise.andThan(v -> holder[0] = v);

        assertEquals(10, holder[0]);
    }

    @Test
    void promiseGotNull() {
        Integer[] holder = new Integer[] { 0 };

        Promise<Integer> future = Promise.of((resolve, reject) -> resolve.accept(null));
        future.andThan(v -> holder[0] = v);

        assertNull(holder[0]);
    }


    @Test
    void promiseWasChained() {
        int[] results = new int[2];

        Promise<Integer> promise = Promise.of((resolve, reject) -> resolve.accept(10));
        promise.andThan(v -> results[0] = v)
               .andThan(v -> results[1] = v);

        assertEquals(10, results[0]);
        assertEquals(10, results[1]);
    }

    @Test
    void promiseGotTheError() {
        int[] value = new int[] { 0 };
        Exception[] error = new Exception[1];

        Promise<Integer> promise = Promise.of((resolve, reject) -> reject.accept(new Exception()));
        promise.andThan(v -> value[0] = v)
              .catchError(e -> error[0] = (Exception) e);

        assertEquals(0, value[0]);
        assertNotNull(error[0]);
    }

    @Test
    void promiseShouldGetTheErrorWhenExceptionIsThrown() {
        int[] value = new int[] { 0 };
        Exception[] error = new Exception[1];

        Promise<Integer> promise = Promise.of((resolve, reject) -> {
            throw new Exception();
        });

        promise.andThan(v -> value[0] = v)
               .catchError(e -> error[0] = (Exception) e);

        assertEquals(0, value[0]);
        assertNotNull(error[0]);
    }

    @Test
    void promiseGotTheResultAsync() throws InterruptedException {
        int[] holder = new int[] { 0 };
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Promise<Integer> promise = Promise.of((resolve, reject) -> new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                reject.accept(e);
                countDownLatch.countDown();
            }
            resolve.accept(10);
            countDownLatch.countDown();
        }).start());

        promise.andThan(v -> holder[0] = v)
               .catchError(Throwable::printStackTrace);

        countDownLatch.await();

        assertEquals(10, holder[0]);
    }
}