package io.github.red.asyncinit;

import io.github.red.asyncinit.annotation.AsyncInitExclude;
import io.github.red.asyncinit.invoker.AsyncInitializeBeanMethodInvoker;
import io.github.red.asyncinit.invoker.AsyncInitializeFactoryBeanMethodInvoker;
import io.github.red.asyncinit.selector.AsyncInitConfigurationSelector;
import io.github.red.asyncinit.utils.ObjectTypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base class providing the core proxy-creation logic for async bean initialization.
 * Subclasses hook into different Spring lifecycle phases to apply this logic.
 *
 * @author red
 */
@Slf4j
public class AsyncInitBeanProxyCreator implements BeanFactoryAware, EnvironmentAware {

    protected ConfigurableListableBeanFactory beanFactory;
    protected List<String> excludeClasses;

    protected final Map<String, Object> cachedProxiedBeanReferences;
    protected final Map<String, Object> cachedOriginalBeanReferences;

    public AsyncInitBeanProxyCreator(Map<String, Object> cachedProxiedBeanReferences,
                                     Map<String, Object> cachedOriginalBeanReferences) {
        this.cachedProxiedBeanReferences = cachedProxiedBeanReferences;
        this.cachedOriginalBeanReferences = cachedOriginalBeanReferences;
    }

    /**
     * Wraps the given bean in an async-init proxy if it qualifies.
     * Returns the original bean unchanged if it does not qualify.
     */
    protected Object wrapIfNecessary(Object bean, String beanName) {
        guardAgainstAsyncThread();

        BeanDefinition beanDefinition;
        try {
            beanDefinition = beanFactory.getMergedBeanDefinition(beanName);
        } catch (Exception e) {
            return bean;
        }

        if (!canBeAsyncInitialized(beanDefinition, bean, beanName)) {
            return bean;
        }

        List<String> initMethods = collectInitMethods(bean, beanDefinition);
        if (!initMethods.isEmpty()) {
            return wrapWithProxy(bean, beanName, initMethods);
        }
        return bean;
    }

    private boolean canBeAsyncInitialized(BeanDefinition bd, Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        String className = targetClass.getName();

        // skip framework internals and auto-configurations
        if (className.startsWith("io.github.red.asyncinit.")
                || className.startsWith("org.springframework.")
                || className.contains("AutoConfiguration")) {
            return false;
        }

        // skip explicitly excluded classes
        if (targetClass.isAnnotationPresent(AsyncInitExclude.class)
                || (excludeClasses != null && excludeClasses.contains(className))) {
            return false;
        }

        // CGLIB requires public, non-final class
        int mod = targetClass.getModifiers();
        if (Modifier.isFinal(mod) || !Modifier.isPublic(mod)) {
            log.debug("[AsyncInit] Skipping {} — not public or is final", className);
            return false;
        }

        // only singleton, eagerly-initialized beans
        if (!bd.isSingleton() || bd.isLazyInit()) {
            return false;
        }

        // CGLIB requires no final non-private/non-static methods
        Class<?> clazz = targetClass;
        while (clazz != Object.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                int m = method.getModifiers();
                if (Modifier.isPrivate(m) || Modifier.isStatic(m)) {
                    continue;
                }
                if (Modifier.isFinal(m)) {
                    log.info("[AsyncInit] Skipping {} — method {} is final", beanName, method.getName());
                    return false;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return true;
    }

    /**
     * Collects all init method names for the given bean
     * (init-method, afterPropertiesSet, @PostConstruct).
     */
    private static List<String> collectInitMethods(Object bean, BeanDefinition bd) {
        List<String> methods = new ArrayList<>();
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        // FactoryBean that produces non-singleton objects is not supported
        if (bean instanceof FactoryBean
                && !((FactoryBean<?>) bean).isSingleton()
                && ObjectTypeUtils.getObjectType((FactoryBean<?>) bean) != null) {
            return Collections.emptyList();
        }

        // 1. XML / @Bean(initMethod = "...")
        String initMethodName = bd.getInitMethodName();
        if (initMethodName != null && !initMethodName.trim().isEmpty()) {
            methods.add(initMethodName);
        }

        // 2. InitializingBean.afterPropertiesSet
        if (bean instanceof InitializingBean) {
            methods.add("afterPropertiesSet");
        }

        // 3. @PostConstruct (traverse class hierarchy)
        Class<?> clazz = targetClass;
        while (clazz != null && clazz != Object.class) {
            Class<?> currentClazz = clazz;
            ReflectionUtils.doWithLocalMethods(currentClazz, method -> {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    methods.add(method.getName());
                }
            });
            clazz = clazz.getSuperclass();
        }

        // validate: none of the collected methods may be private or static
        for (String name : methods) {
            Class<?> current = targetClass;
            while (current != Object.class) {
                try {
                    Method m = current.getDeclaredMethod(name);
                    int mod = m.getModifiers();
                    if (Modifier.isPrivate(mod) || Modifier.isStatic(mod)) {
                        log.debug("[AsyncInit] Skipping {} — init method {} is private or static",
                                targetClass, name);
                        return Collections.emptyList();
                    }
                    break;
                } catch (NoSuchMethodException e) {
                    if (current.getSuperclass() == Object.class) {
                        log.warn("[AsyncInit] Cannot find method {} in {}", name, targetClass);
                        return Collections.emptyList();
                    }
                }
                current = current.getSuperclass();
            }
        }

        return methods;
    }

    /**
     * Creates a CGLIB proxy around {@code bean} that intercepts its init methods
     * and submits them for async execution.
     */
    public static Object wrapWithProxy(Object bean, String beanName, List<String> initMethods) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ProxyFactory pf = new ProxyFactory();
        pf.setTargetClass(targetClass);
        pf.setTarget(bean);
        pf.setProxyTargetClass(true);
        if (bean instanceof FactoryBean) {
            pf.addAdvice(new AsyncInitializeFactoryBeanMethodInvoker((FactoryBean<?>) bean, beanName, initMethods));
        } else {
            pf.addAdvice(new AsyncInitializeBeanMethodInvoker(bean, beanName, initMethods));
        }
        log.info("[AsyncInit] Wrapping bean '{}' ({}), initMethods: {}", beanName, bean.getClass(), initMethods);
        return pf.getProxy();
    }

    /**
     * Prevents beans from calling applicationContext.getBean() inside an async init thread,
     * which would trigger recursive proxy creation.
     */
    private void guardAgainstAsyncThread() {
        String threadName = Thread.currentThread().getName();
        if (threadName != null && threadName.contains("asyncinit-thread")) {
            throw new IllegalStateException(
                    "[AsyncInit] Calling applicationContext.getBean() inside an async init thread is not allowed. "
                            + "Use @Autowired for injection, or annotate the bean class with @AsyncInitExclude.");
        }
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.notNull(beanFactory, "beanFactory must not be null");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.excludeClasses = environment.getProperty(
                AsyncInitConfigurationSelector.EXCLUDE_CLASSES_PROPERTY, List.class);
    }
}
