package io.github.red.asyncinit.task;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

/**
 * Global registry of all pending async-init tasks, keyed by bean name.
 *
 * @author red
 */
@Slf4j
public class AsyncInitializeTaskHolder {

    private static final ConcurrentMap<String, AsyncInitializeTask> TASKS = new ConcurrentHashMap<>(32);

    /**
     * Registers a new task for the given bean.
     * Called from the {@link io.github.red.asyncinit.invoker.AsyncInitializeBeanMethodInvoker}
     * constructor when the proxy is created.
     */
    public static void registerTask(String beanName, List<String> initMethodNames, Object target) {
        if (initMethodNames == null || initMethodNames.isEmpty()) {
            throw new IllegalArgumentException("[AsyncInit] initMethodNames must not be empty for bean: " + beanName);
        }
        if (TASKS.containsKey(beanName)) {
            log.warn("[AsyncInit] Duplicate task registration for bean '{}', ignoring", beanName);
            return;
        }
        TASKS.put(beanName, new AsyncInitializeTask(initMethodNames, target));
    }

    /**
     * Records one intercepted init method call. When all expected init methods have been
     * intercepted, the task is submitted to the thread pool.
     */
    public static void callInitMethod(String beanName, MethodInvocation invocation) {
        AsyncInitializeTask task = requireTask(beanName);
        int remaining = task.callInitMethod(invocation.getMethod().getName(), invocation);
        if (remaining == 0) {
            AsyncInitializeTaskExecutor.submitTask(beanName, task);
            log.info("[AsyncInit] Submitted task for bean '{}'", beanName);
        }
    }

    /** Blocks the calling thread until the named bean's init task completes. */
    public static void waitTaskComplete(String beanName) throws InterruptedException {
        CountDownLatch latch = requireTask(beanName).getLatch();
        latch.await();
    }

    /** Returns whether the named bean's init task is currently running. */
    public static boolean taskIsInitializing(String beanName) {
        AsyncInitializeTask task = TASKS.get(beanName);
        return task != null && task.isInitializing();
    }

    /** Returns the total number of registered tasks. */
    public static int getTaskCount() {
        return TASKS.size();
    }

    /** Logs statistics and clears the registry. Called after all tasks have finished. */
    public static void destroy() {
        log.info("[AsyncInit] {} beans were async-initialized:", TASKS.size());
        TASKS.forEach((name, task) -> log.info("[AsyncInit]   - {}", name));
        TASKS.clear();
    }

    private static AsyncInitializeTask requireTask(String beanName) {
        AsyncInitializeTask task = TASKS.get(beanName);
        if (task == null) {
            throw new IllegalStateException(
                    "[AsyncInit] No task registered for bean '" + beanName
                            + "'. Registered beans: " + TASKS.keySet());
        }
        return task;
    }
}
