package org.hermesprd.workbench.task;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class TaskMutationGate {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T execute(String taskId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(taskId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void execute(String taskId, Runnable action) {
        execute(taskId, () -> {
            action.run();
            return null;
        });
    }
}
