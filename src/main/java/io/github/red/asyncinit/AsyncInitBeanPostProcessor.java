package io.github.red.asyncinit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import java.util.Map;

/**
 * {@link BeanPostProcessor} that wraps qualifying beans in an async-init proxy.
 *
 * <h3>Circular dependency handling</h3>
 * When bean A is involved in a circular dependency, Spring calls
 * {@link AsyncInitBeanSmartInstantiationPostProcessor#getEarlyBeanReference} before this
 * processor runs, creating the proxy early and caching it in {@code cachedProxiedBeanReferences}.
 *
 * <p><b>Before phase:</b> if a cached proxy exists, reuse it (don't create a second proxy),
 * and save the original bean in {@code cachedOriginalBeanReferences} for the After phase.</p>
 *
 * <p><b>After phase:</b> if a cached original exists, return it so that Spring's
 * {@code exposedObject == bean} check passes, allowing the framework to restore the
 * proxy from {@code earlySingletonObjects}.</p>
 *
 * @author red
 * @see AsyncInitBeanSmartInstantiationPostProcessor
 */
@Slf4j
public class AsyncInitBeanPostProcessor extends AsyncInitBeanProxyCreator
        implements BeanPostProcessor, PriorityOrdered {

    public AsyncInitBeanPostProcessor(Map<String, Object> cachedProxiedBeanReferences,
                                      Map<String, Object> cachedOriginalBeanReferences) {
        super(cachedProxiedBeanReferences, cachedOriginalBeanReferences);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean == null) {
            return null;
        }

        // circular dep: proxy was already created in getEarlyBeanReference — reuse it
        Object proxiedBean = cachedProxiedBeanReferences.get(beanName);
        if (proxiedBean != null) {
            cachedOriginalBeanReferences.put(beanName, bean);
            return proxiedBean;
        }

        return wrapIfNecessary(bean, beanName);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // circular dep: return the original bean so Spring's exposedObject == bean check passes,
        // the proxy is then restored via Spring's earlySingletonReference mechanism
        Object originalBean = cachedOriginalBeanReferences.get(beanName);
        if (originalBean != null) {
            return originalBean;
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
