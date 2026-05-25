package io.github.red.asyncinit.invoker;

import io.github.red.asyncinit.task.AsyncInitializeTaskExecutor;
import io.github.red.asyncinit.task.AsyncInitializeTaskHolder;
import io.github.red.asyncinit.utils.MethodInvocationWrapper;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.util.List;

/**
 * {@link MethodInterceptor} for ordinary (non-FactoryBean) beans.
 *
 * <ul>
 *   <li>Intercepts init methods and submits them asynchronously.</li>
 *   <li>Blocks non-init method calls until initialization completes.</li>
 *   <li>After the Spring context is refreshed ({@link AsyncInitializeTaskExecutor#isDestroyed()}),
 *       all calls are passed through directly.</li>
 * </ul>
 *
 * @author red
 */
@Slf4j
public class AsyncInitializeBeanMethodInvoker implements MethodInterceptor {

    private final Object targetObject;
    private final String beanName;
    private final List<String> initMethodNames;

    public AsyncInitializeBeanMethodInvoker(Object targetObject, String beanName,
                                            List<String> initMethodNames) {
        this.targetObject = targetObject;
        this.beanName = beanName;
        this.initMethodNames = initMethodNames;
        AsyncInitializeTaskHolder.registerTask(beanName, initMethodNames, targetObject);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // context already refreshed — pass through directly
        if (AsyncInitializeTaskExecutor.isDestroyed()) {
            return MethodInvocationWrapper.invoke(targetObject, invocation);
        }

        invocation.getMethod().setAccessible(true);
        String methodName = invocation.getMethod().getName();

        if (initMethodNames.contains(methodName)) {
            // intercept: submit to thread pool instead of running synchronously
            AsyncInitializeTaskHolder.callInitMethod(beanName, invocation);
            return null;
        }

        if (AsyncInitializeTaskHolder.taskIsInitializing(beanName)) {
            // block: a non-init method was called while init is still running
            long start = System.currentTimeMillis();
            AsyncInitializeTaskHolder.waitTaskComplete(beanName);
            log.info("[AsyncInit] '{}' method '{}' blocked {}ms waiting for init",
                    beanName, methodName, System.currentTimeMillis() - start);
        }

        return MethodInvocationWrapper.invoke(targetObject, invocation);
    }
}
