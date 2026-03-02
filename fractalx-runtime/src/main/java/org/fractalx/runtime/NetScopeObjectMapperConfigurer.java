package org.fractalx.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * Registers {@link JavaTimeModule} on NetScope's internal {@link ObjectMapper}.
 *
 * <p>NetScope's {@code NetScopeInvoker} creates its own plain {@code new ObjectMapper()}
 * which has no Java-time support. This post-processor reads the private {@code objectMapper}
 * field via reflection and mutates the existing (mutable) instance — no field replacement
 * needed — so that {@link java.time.LocalDateTime} and sibling types are serialised as
 * ISO-8601 strings rather than throwing {@code InvalidDefinitionException}.
 *
 * <p>Only activates when {@code NetScopeInvoker} is on the classpath (i.e. when
 * {@code netscope-server} is a dependency).
 */
@Component
@ConditionalOnClass(name = "org.fractalx.netscope.server.core.NetScopeInvoker")
public class NetScopeObjectMapperConfigurer implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(NetScopeObjectMapperConfigurer.class);
    private static final String INVOKER_CLASS = "org.fractalx.netscope.server.core.NetScopeInvoker";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!bean.getClass().getName().equals(INVOKER_CLASS)) {
            return bean;
        }
        try {
            Field field = bean.getClass().getDeclaredField("objectMapper");
            field.setAccessible(true);
            ObjectMapper mapper = (ObjectMapper) field.get(bean);
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            log.debug("NetScopeObjectMapperConfigurer: registered JavaTimeModule on NetScopeInvoker");
        } catch (Exception e) {
            log.warn("NetScopeObjectMapperConfigurer: could not configure JavaTimeModule — {}", e.getMessage());
        }
        return bean;
    }
}
