package io.github.red.asyncinit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;

import java.util.Map;

/**
 * Handles circular dependency by creating the async-init proxy during the early bean
 * reference exposure phase ({@code getEarlyBeanReference}).
 *
 * <p>This processor intentionally does <em>not</em> implement {@link org.springframework.core.Ordered}
 * or {@link org.springframework.core.PriorityOrdered}. It must run <em>after</em> Spring AOP's
 * {@link org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator} so that the
 * async-init proxy wraps the AOP proxy, preserving both layers of advice.</p>
 *
 * @author red
 * @see AsyncInitBeanPostProcessor
 */
@Slf4j
public class AsyncInitBeanSmartInstantiationPostProcessor extends AsyncInitBeanProxyCreator
        implements SmartInstantiationAwareBeanPostProcessor {

    public AsyncInitBeanSmartInstantiationPostProcessor(Map<String, Object> cachedProxiedBeanReferences,
                                                        Map<String, Object> cachedOriginalBeanReferences) {
        super(cachedProxiedBeanReferences, cachedOriginalBeanReferences);
    }

    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
        // idempotent: return cached proxy if already created
        Object cached = cachedProxiedBeanReferences.get(beanName);
        if (cached != null) {
            return cached;
        }

        Object maybeWrapped = wrapIfNecessary(bean, beanName);
        cachedProxiedBeanReferences.put(beanName, maybeWrapped);
        return maybeWrapped;
    }
}
