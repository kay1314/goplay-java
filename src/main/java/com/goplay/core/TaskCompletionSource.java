package com.goplay.core;

/**
 * TaskCompletionSource provides a way to create a task that can be completed from outside.
 * Similar to C# TaskCompletionSource.
 */
public class TaskCompletionSource<T> {
    private final Object lock = new Object();
    private T result;
    private Exception exception;
    private boolean isCompleted = false;

    /**
     * Wait for the task to complete and return the result.
     */
    public T getResult() throws InterruptedException, Exception {
        synchronized (lock) {
            while (!isCompleted) {
                lock.wait();
            }
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    /**
     * Wait for the task with timeout.
     */
    public T getResult(long timeoutMs) throws InterruptedException, Exception {
        synchronized (lock) {
            if (!isCompleted) {
                lock.wait(timeoutMs);
            }
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    /**
     * Set the result and complete the task.
     */
    public void setResult(T value) {
        synchronized (lock) {
            if (isCompleted) {
                return;
            }
            this.result = value;
            this.isCompleted = true;
            lock.notifyAll();
        }
    }

    /**
     * Set exception and complete the task.
     */
    public void setException(Exception ex) {
        synchronized (lock) {
            if (isCompleted) {
                return;
            }
            this.exception = ex;
            this.isCompleted = true;
            lock.notifyAll();
        }
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    /**
     * Get result without waiting (may return null if not completed).
     */
    public T tryGetResult() {
        synchronized (lock) {
            return isCompleted ? result : null;
        }
    }
}
