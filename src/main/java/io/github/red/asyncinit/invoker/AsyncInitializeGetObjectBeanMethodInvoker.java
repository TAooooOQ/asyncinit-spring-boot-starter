package io.github.red.asyncinit.invoker;

import io.github.red.asyncinit.task.AsyncInitializeTaskExecutor;
import io.github.red.asyncinit.task.AsyncInitializeTaskHolder;
import io.github.red.asyncinit.utils.MethodInvocationWrapper;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.FactoryBean;

/**
 * {@link MethodInterceptor} for the object produced by an async-initialized {@link FactoryBean}.
 *
 * <p>Blocks all method calls until the parent FactoryBean has finished its own async init,
 * then obtains the real object via {@link FactoryBean#getObject()} and delegates.</p>
 *
 * @author red
 */
@Slf4j
public class AsyncInitializeGetObjectBeanMethodInvoker implements MethodInterceptor {

    private final FactoryBean<?> factoryBean;
    private final String factoryBeanName;

    /** The real object produced by the FactoryBean, lazily initialized. */
    private volatile Object targetObject;

    public AsyncInitializeGetObjectBeanMethodInvoker(FactoryBean<?> factoryBean, String factoryBeanName) {
        this.factoryBean = factoryBean;
        this.factoryBeanName = factoryBeanName;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (AsyncInitializeTaskExecutor.isDestroyed()) {
            return MethodInvocationWrapper.invoke(resolveTarget(), invocation);
        }

        invocation.getMethod().setAccessible(true);
        String methodName = invocation.getMethod().getName();

        if (AsyncInitializeTaskHolder.taskIsInitializing(factoryBeanName)) {
            long start = System.currentTimeMillis();
            AsyncInitializeTaskHolder.waitTaskComplete(factoryBeanName);
            log.info("[AsyncInit] Object from FactoryBean '{}' method '{}' blocked {}ms waiting for factory init",
                    factoryBeanName, methodName, System.currentTimeMillis() - start);
        }

        return MethodInvocationWrapper.invoke(resolveTarget(), invocation);
    }

    private Object resolveTarget() throws Exception {
        if (targetObject == null) {
            synchronized (this) {
                if (targetObject == null) {
                    targetObject = factoryBean.getObject();
                }
            }
        }
        return targetObject;
    }
}
