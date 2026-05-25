package io.github.red.asyncinit.utils;

import org.springframework.beans.factory.FactoryBean;

/**
 * Utility for resolving the object type produced by a {@link FactoryBean}.
 *
 * @author red
 */
public final class ObjectTypeUtils {

    private ObjectTypeUtils() {
    }

    /**
     * Returns the type of the object produced by {@code factoryBean}, or {@code null} if unknown.
     */
    public static Class<?> getObjectType(FactoryBean<?> factoryBean) {
        return factoryBean.getObjectType();
    }
}
