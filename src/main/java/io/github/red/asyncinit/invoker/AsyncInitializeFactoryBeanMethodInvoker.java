package io.github.red.asyncinit.invoker;

import io.github.red.asyncinit.task.AsyncInitializeTaskExecutor;
import io.github.red.asyncinit.task.AsyncInitializeTaskHolder;
import io.github.red.asyncinit.utils.MethodInvocationWrapper;
import io.github.red.asyncinit.utils.ObjectTypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.FactoryBean;

import java.util.List;

/**
 * {@link MethodInterceptor} for {@link FactoryBean} beans.
 *
 * <p>Special considerations compared to ordinary beans:</p>
 * <ul>
 *   <li>{@code isSingleton()} and {@code getObjectType()} are always passed through immediately
 *       to avoid blocking Spring's main thread during refresh.</li>
 *   <li>{@code getObject()} returns a second-level proxy that blocks until the FactoryBean's
 *       own init methods have completed before delegating to the real object.</li>
 * </ul>
 *
 * @author red
 */
@Slf4j
public class AsyncInitializeFactoryBeanMethodInvoker implements MethodInterceptor {

    private final FactoryBean<?> targetObject;
    private final String beanName;
    private final List<String> initMethodNames;

    /** Lazily created proxy for the object produced by this FactoryBean. */
    private volatile Object getObjectProxy;

    public AsyncInitializeFactoryBeanMethodInvoker(FactoryBean<?> targetObject, String beanName,
                                                   List<String> initMethodNames) {
        this.targetObject = targetObject;
        this.beanName = beanName;
        this.initMethodNames = initMethodNames;
        AsyncInitializeTaskHolder.registerTask(beanName, initMethodNames, targetObject);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (AsyncInitializeTaskExecutor.isDestroyed()) {
            return MethodInvocationWrapper.invoke(targetObject, invocation);
        }

        invocation.getMethod().setAccessible(true);
        String methodName = invocation.getMethod().getName();
        int paramCount = invocation.getMethod().getParameterCount();

        // always pass through to avoid blocking Spring's main refresh thread
        if (paramCount == 0 && ("isSingleton".equals(methodName) || "getObjectType".equals(methodName))) {
            return MethodInvocationWrapper.invoke(targetObject, invocation);
        }

        // getObject() — return a proxy that waits for FactoryBean init before delegating
        if ("getObject".equals(methodName) && paramCount == 0) {
            Class<?> objectType = ObjectTypeUtils.getObjectType(targetObject);
            if (objectType == null) {
                // object type is unknown; wait for init and return the real object directly
                if (AsyncInitializeTaskHolder.taskIsInitializing(beanName)) {
                    AsyncInitializeTaskHolder.waitTaskComplete(beanName);
                }
                return targetObject.getObject();
            }
            if (getObjectProxy == null) {
                synchronized (this) {
                    if (getObjectProxy == null) {
                        AsyncInitializeGetObjectBeanMethodInvoker invoker =
                                new AsyncInitializeGetObjectBeanMethodInvoker(targetObject, beanName);
                        ProxyFactory pf = new ProxyFactory();
                        pf.setTargetClass(objectType);
                        pf.setProxyTargetClass(true);
                        pf.addAdvice(invoker);
                        getObjectProxy = pf.getProxy();
                    }
                }
            }
            return getObjectProxy;
        }

        if (initMethodNames.contains(methodName)) {
            AsyncInitializeTaskHolder.callInitMethod(beanName, invocation);
            return null;
        }

        if (AsyncInitializeTaskHolder.taskIsInitializing(beanName)) {
            long start = System.currentTimeMillis();
            AsyncInitializeTaskHolder.waitTaskComplete(beanName);
            log.info("[AsyncInit] FactoryBean '{}' method '{}' blocked {}ms waiting for init",
                    beanName, methodName, System.currentTimeMillis() - start);
        }

        return MethodInvocationWrapper.invoke(targetObject, invocation);
    }
}
