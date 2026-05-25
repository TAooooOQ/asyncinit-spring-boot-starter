package io.github.red.asyncinit.task;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * Runnable representing the async initialization work for a single bean.
 *
 * <p>A task is registered when the proxy is created, populated as Spring calls each
 * init method (which the proxy intercepts), and submitted to the thread pool once all
 * expected init methods have been collected.</p>
 *
 * @author red
 */
@Slf4j
@ToString
public class AsyncInitializeTask implements Runnable {

    /** Released when this task finishes (success or failure). */
    private final CountDownLatch latch = new CountDownLatch(1);

    /** Method invocations to execute, in the order they were collected. */
    private final LinkedList<MethodInvocation> initInvocations = new LinkedList<>();

    /** Names still expected; decremented as each init method call is intercepted. */
    private final List<String> pendingMethodNames;

    private final Object targetObject;

    /** Futures of tasks that must complete before this task starts. */
    private final Set<Future<?>> dependencies = new HashSet<>();

    private volatile boolean initializing = false;

    AsyncInitializeTask(List<String> initMethodNames, Object targetObject) {
        this.pendingMethodNames = initMethodNames;
        this.targetObject = targetObject;
    }

    @Override
    public void run() {
        try {
            for (Future<?> dep : dependencies) {
                dep.get();
            }
            for (MethodInvocation invocation : initInvocations) {
                log.info("[AsyncInit] Executing init method '{}' on {}",
                        invocation.getMethod().getName(), targetObject.getClass());
                invocation.getMethod().invoke(targetObject, invocation.getArguments());
            }
        } catch (Throwable e) {
            Throwable cause = unwrap(e);
            log.error("[AsyncInit] Init method failed on {}: {}", targetObject.getClass(), cause.getMessage(), cause);
            AsyncInitializeTaskExecutor.destroy();
            AsyncInitializeTaskExecutor.reportException(cause);
        } finally {
            initializing = false;
            latch.countDown();
        }
    }

    /**
     * Records one intercepted init method call.
     *
     * @return number of init method calls still pending; 0 means ready to submit
     */
    int callInitMethod(String methodName, MethodInvocation invocation) {
        initializing = true;
        if (pendingMethodNames.contains(methodName)) {
            pendingMethodNames.remove(methodName);
            initInvocations.addLast(invocation);
        } else {
            log.warn("[AsyncInit] Init method '{}' called more than once on {}", methodName, targetObject);
        }
        return pendingMethodNames.size();
    }

    public void addDependency(Future<?> future) {
        dependencies.add(future);
    }

    CountDownLatch getLatch() {
        return latch;
    }

    boolean isInitializing() {
        return initializing;
    }

    public Object getTargetObject() {
        return targetObject;
    }

    private static Throwable unwrap(Throwable e) {
        if (e instanceof InvocationTargetException) {
            return ((InvocationTargetException) e).getTargetException();
        }
        return e;
    }
}
