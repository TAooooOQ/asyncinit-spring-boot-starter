package io.github.red.asyncinit.task.processor;

import io.github.red.asyncinit.task.AsyncInitializeTask;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Extension point that runs before an async-init task is submitted to the thread pool.
 *
 * <p>Implement this interface to establish task ordering dependencies or perform
 * custom pre-submission work. Register implementations in
 * {@link io.github.red.asyncinit.task.AsyncInitializeTaskExecutor}.</p>
 *
 * @author red
 */
public interface IAsyncTaskPostProcessor {

    /**
     * Called just before {@code task} is submitted to the executor.
     *
     * @param beanName           the name of the bean being initialized
     * @param task               the task about to be submitted
     * @param submittedFutures   futures of tasks already submitted, keyed by bean name
     */
    void postProcessBeforeTaskSubmit(String beanName, AsyncInitializeTask task,
                                     Map<String, Future<?>> submittedFutures);
}
