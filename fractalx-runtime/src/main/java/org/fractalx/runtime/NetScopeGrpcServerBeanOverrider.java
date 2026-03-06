package org.fractalx.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Replaces the {@code netScopeGrpcServer} bean definition (registered by
 * {@code NetScopeAutoConfiguration}) with {@link CorrelationAwareNetScopeGrpcServer}.
 *
 * <p>A {@link BeanFactoryPostProcessor} runs after all bean definitions are registered
 * but before any beans are instantiated.  This gives us a clean interception point that
 * avoids both the {@code @ConditionalOnMissingBean} ordering problem and the
 * {@code @PostConstruct} timing issue that a {@link org.springframework.beans.factory.config.BeanPostProcessor}
 * would face.
 *
 * <p>Constructor-mode autowiring resolves {@link org.fractalx.netscope.server.config.NetScopeConfig},
 * {@link org.fractalx.netscope.server.grpc.NetScopeGrpcServiceImpl}, and
 * {@link NetScopeContextInterceptor} by type — no bean name strings involved.
 */
@Component
@ConditionalOnClass(name = "org.fractalx.netscope.server.grpc.NetScopeGrpcServer")
public class NetScopeGrpcServerBeanOverrider implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(NetScopeGrpcServerBeanOverrider.class);
    private static final String BEAN_NAME = "netScopeGrpcServer";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            log.warn("NetScopeGrpcServerBeanOverrider: BeanFactory is not a BeanDefinitionRegistry — skipping");
            return;
        }

        if (!registry.containsBeanDefinition(BEAN_NAME)) {
            log.debug("NetScopeGrpcServerBeanOverrider: bean '{}' not found — skipping", BEAN_NAME);
            return;
        }

        AbstractBeanDefinition replacement = BeanDefinitionBuilder
                .genericBeanDefinition(CorrelationAwareNetScopeGrpcServer.class)
                .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR)
                .getBeanDefinition();

        registry.removeBeanDefinition(BEAN_NAME);
        registry.registerBeanDefinition(BEAN_NAME, replacement);

        log.info("NetScopeGrpcServerBeanOverrider: replaced '{}' with CorrelationAwareNetScopeGrpcServer", BEAN_NAME);
    }
}
