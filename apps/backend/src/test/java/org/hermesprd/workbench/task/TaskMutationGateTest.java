package org.hermesprd.workbench.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskMutationGateTest {
    @Test
    void serializesTwoMutationsForTheSameTask() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            TaskMutationGate gate = new TaskMutationGate();
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> runCreation = executor.submit(() -> gate.execute("task_1", () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                }));
                assertTrue(firstEntered.await(1, TimeUnit.SECONDS));
                Future<?> sourceMutation = executor.submit(() -> gate.execute("task_1", secondEntered::countDown));

                assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));
                releaseFirst.countDown();
                runCreation.get(1, TimeUnit.SECONDS);
                sourceMutation.get(1, TimeUnit.SECONDS);
                assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Test
    void allowsConcurrentMutationsForDifferentTasks() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            TaskMutationGate gate = new TaskMutationGate();
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                // task_1 blocks until releaseFirst
                Future<?> task1 = executor.submit(() -> gate.execute("task_1", () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                }));
                assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

                // task_2 should NOT be blocked by task_1
                Future<?> task2 = executor.submit(() -> gate.execute("task_2", secondEntered::countDown));
                assertTrue(secondEntered.await(500, TimeUnit.MILLISECONDS));

                releaseFirst.countDown();
                task1.get(1, TimeUnit.SECONDS);
                task2.get(1, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }
}
