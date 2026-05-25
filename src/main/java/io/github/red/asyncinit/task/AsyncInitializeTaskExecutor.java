package io.github.red.asyncinit.task;

import io.github.red.asyncinit.task.processor.IAsyncTaskPostProcessor;
import io.github.red.asyncinit.task.processor.MybatisPlusTaskPostProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the thread pool used to run async init tasks.
 *
 * <p>All state is static because the thread pool is global to the Spring context.
 * The executor is lazily created on first task submission and destroyed once
 * {@link io.github.red.asyncinit.listener.AsyncTaskExecutionListener} confirms all tasks
 * have finished.</p>
 *
 * <h3>Thread pool parameters</h3>
 * <ul>
 *   <li>Core/max size: {@code CPU + 1}</li>
 *   <li>Queue: {@link SynchronousQueue} — no buffering; tasks run immediately or trigger
 *       {@link ThreadPoolExecutor.CallerRunsPolicy}</li>
 *   <li>Rejection: CallerRunsPolicy — degrades to synchronous execution on the main thread
 *       rather than dropping tasks</li>
 * </ul>
 *
 * @author red
 */
@Slf4j
public class AsyncInitializeTaskExecutor {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final AtomicReference<ThreadPoolExecutor> POOL = new AtomicReference<>();
    private static final ConcurrentMap<String, Future<?>> FUTURES = new ConcurrentHashMap<>();
    private static final AtomicBoolean DESTROYED = new AtomicBoolean(false);
    private static final AtomicReference<Throwable> INIT_EXCEPTION = new AtomicReference<>();

    private static final List<IAsyncTaskPostProcessor> POST_PROCESSORS = new ArrayList<>();

    static {
        POST_PROCESSORS.add(new MybatisPlusTaskPostProcessor());
    }

    static void submitTask(String beanName, AsyncInitializeTask task) {
        POST_PROCESSORS.forEach(p -> p.postProcessBeforeTaskSubmit(beanName, task, FUTURES));

        if (POOL.get() == null) {
            ThreadPoolExecutor pool = createPool();
            if (!POOL.compareAndSet(null, pool)) {
                pool.shutdown(); // lost the race; discard the extra pool
            }
        }

        Future<?> future = POOL.get().submit(task);
        FUTURES.put(beanName, future);
    }

    /**
     * Waits for all submitted tasks to complete, then destroys the executor.
     * Rethrows any exception thrown by an async init task.
     */
    public static void waitForAllTasksFinished() throws Throwable {
        for (Future<?> future : FUTURES.values()) {
            future.get();
        }

        Throwable ex = INIT_EXCEPTION.get();
        if (ex != null) {
            throw ex;
        }

        if (!DESTROYED.get() && FUTURES.size() != AsyncInitializeTaskHolder.getTaskCount()) {
            throw new IllegalStateException(String.format(
                    "[AsyncInit] Submitted %d futures but registered %d tasks — internal inconsistency",
                    FUTURES.size(), AsyncInitializeTaskHolder.getTaskCount()));
        }

        log.info("[AsyncInit] All async init tasks finished");
        destroy();
    }

    /** Records the first exception thrown by any async init task. */
    public static void reportException(Throwable e) {
        INIT_EXCEPTION.compareAndSet(null, e);
    }

    /** Marks the executor as destroyed and shuts down the thread pool. */
    public synchronized static void destroy() {
        DESTROYED.set(true);
        FUTURES.clear();
        ThreadPoolExecutor pool = POOL.getAndSet(null);
        if (pool != null) {
            pool.shutdown();
        }
        log.info("[AsyncInit] TaskExecutor destroyed");
    }

    /** Returns true after {@link #destroy()} has been called (i.e., after context refresh). */
    public static boolean isDestroyed() {
        return DESTROYED.get();
    }

    private static ThreadPoolExecutor createPool() {
        int size = CPU_COUNT + 1;
        AtomicInteger index = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "asyncinit-thread-" + index.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(size, size, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), factory, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
