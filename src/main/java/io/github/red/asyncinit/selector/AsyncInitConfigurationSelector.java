package io.github.red.asyncinit.selector;

import io.github.red.asyncinit.annotation.EnableAsyncInit;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Selects whether {@link io.github.red.asyncinit.AsyncInitAutoConfiguration} is imported
 * based on the active Spring profiles declared in {@link EnableAsyncInit#enabledProfiles()}.
 *
 * @author red
 */
public class AsyncInitConfigurationSelector implements ImportSelector, EnvironmentAware {

    private static final String ASYNC_INIT_CONFIGURATION =
            "io.github.red.asyncinit.AsyncInitAutoConfiguration";

    private static final String EMPTY_CONFIGURATION =
            "io.github.red.asyncinit.EmptyConfiguration";

    public static final String EXCLUDE_CLASSES_PROPERTY = "asyncinit.excludeClasses";

    private ConfigurableEnvironment environment;

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        if (!metadata.isAnnotated(EnableAsyncInit.class.getName())) {
            return new String[]{EMPTY_CONFIGURATION};
        }

        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableAsyncInit.class.getName());
        if (attrs == null || attrs.isEmpty()) {
            return new String[]{EMPTY_CONFIGURATION};
        }

        String[] enabledProfiles = (String[]) attrs.get("enabledProfiles");

        // empty means active in all profiles
        if (enabledProfiles != null && enabledProfiles.length > 0) {
            Set<String> activeProfiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
            boolean anyMatch = Arrays.stream(enabledProfiles).anyMatch(activeProfiles::contains);
            if (!anyMatch) {
                return new String[]{EMPTY_CONFIGURATION};
            }
        }

        // propagate excludeClasses into the Environment so AsyncInitBeanProxyCreator can read it
        String[] excludeClasses = (String[]) attrs.get("excludeClasses");
        Properties props = new Properties();
        props.put(EXCLUDE_CLASSES_PROPERTY, Arrays.asList(excludeClasses != null ? excludeClasses : new String[0]));
        environment.getPropertySources().addLast(new PropertiesPropertySource("asyncInitProperties", props));

        return new String[]{ASYNC_INIT_CONFIGURATION};
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = (ConfigurableEnvironment) environment;
    }
}
