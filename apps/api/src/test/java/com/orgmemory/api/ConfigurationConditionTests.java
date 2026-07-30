package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @ConditionalOnBean} is only meaningful inside {@code @AutoConfiguration}.
 *
 * <p>Boot processes user configuration first and auto-configuration afterwards, so a bean
 * condition written in a component-scanned {@code @Configuration} resolves against whatever
 * scan order happened to register by then. Against anything auto-configuration contributes —
 * {@code MeterRegistry}, {@code OpenTelemetry}, a {@code DataSource} — it is not merely
 * unreliable, it is always false.
 *
 * <p>This is a silent failure, which is why it needs a test rather than a review note. The bean
 * simply does not exist; nothing logs, nothing fails, and the absence surfaces only as a
 * dashboard panel that never had data. That is exactly how
 * {@code orgmemory.assistant.time_to_first_token} was lost — the handler was written, merged,
 * deployed and never once registered. {@code GraphRagObservabilityAutoConfiguration} shows the
 * shape that works when a bean condition genuinely is the right tool.
 */
class ConfigurationConditionTests {

    @Test
    void applicationConfigurationDoesNotUseBeanConditions() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));

        List<String> offenders = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.orgmemory.api")) {
            Class<?> type;
            try {
                type = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException | NoClassDefFoundError unavailable) {
                continue;
            }
            if (type.isAnnotationPresent(AutoConfiguration.class)) {
                continue;
            }
            if (type.isAnnotationPresent(ConditionalOnBean.class)) {
                offenders.add(type.getName());
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ConditionalOnBean.class)) {
                    offenders.add(type.getName() + "#" + method.getName());
                }
            }
        }

        assertTrue(
                offenders.isEmpty(),
                () -> "@ConditionalOnBean in configuration Boot processes before "
                        + "auto-configuration is always false against an auto-configured bean, "
                        + "and removes the bean silently: " + offenders);
    }
}
