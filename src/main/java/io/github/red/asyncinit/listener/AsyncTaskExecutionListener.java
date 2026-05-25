package io.github.red.asyncinit.listener;

import io.github.red.asyncinit.task.AsyncInitializeTaskExecutor;
import io.github.red.asyncinit.task.AsyncInitializeTaskHolder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * Waits for all async-init tasks to complete once the Spring context has been refreshed.
 *
 * <p>By implementing {@link PriorityOrdered} with {@code HIGHEST_PRECEDENCE + 1}, this
 * listener runs before any other {@link ContextRefreshedEvent} listener, ensuring async
 * initialization completes before the application is considered ready.</p>
 *
 * <p>If any async init task threw an exception, it is rethrown here, causing the
 * Spring context to fail to start.</p>
 *
 * @author red
 */
@Slf4j
public class AsyncTaskExecutionListener
        implements PriorityOrdered, ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!applicationContext.equals(event.getApplicationContext())) {
            return;
        }
        AsyncInitializeTaskExecutor.waitForAllTasksFinished();
        AsyncInitializeTaskHolder.destroy();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
