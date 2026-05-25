package io.github.red.asyncinit.task.processor;

import io.github.red.asyncinit.task.AsyncInitializeTask;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Ensures that {@code MybatisSqlSessionFactoryBean} runs only after any {@code dataSource}
 * bean has finished its own async initialization.
 *
 * <p>MyBatis-Plus's {@code MybatisSqlSessionFactoryBean.afterPropertiesSet()} requires a
 * fully initialized {@link javax.sql.DataSource}. If the DataSource is also being
 * async-initialized concurrently, MyBatis-Plus would see an uninitialized DataSource.</p>
 *
 * @author red
 */
public class MybatisPlusTaskPostProcessor implements IAsyncTaskPostProcessor {

    private static final String MYBATIS_PLUS_FACTORY_CLASS =
            "com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean";

    @Override
    public void postProcessBeforeTaskSubmit(String beanName, AsyncInitializeTask task,
                                            Map<String, Future<?>> submittedFutures) {
        if (!MYBATIS_PLUS_FACTORY_CLASS.equals(task.getTargetObject().getClass().getName())) {
            return;
        }
        // depend on all already-submitted DataSource tasks
        submittedFutures.forEach((name, future) -> {
            if (name.toLowerCase().contains("datasource")) {
                task.addDependency(future);
            }
        });
    }
}
