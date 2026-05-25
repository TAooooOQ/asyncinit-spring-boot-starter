package io.github.red.asyncinit.utils;

import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.InvocationTargetException;

/**
 * Utility for invoking a method on a target object via reflection while unwrapping
 * {@link InvocationTargetException} so that callers see the original exception.
 *
 * @author red
 */
public final class MethodInvocationWrapper {

    private MethodInvocationWrapper() {
    }

    /**
     * Invokes {@code invocation.getMethod()} on {@code target} and returns the result.
     * {@link InvocationTargetException} is unwrapped so the root cause propagates.
     */
    public static Object invoke(Object target, MethodInvocation invocation) throws Throwable {
        try {
            return invocation.getMethod().invoke(target, invocation.getArguments());
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
