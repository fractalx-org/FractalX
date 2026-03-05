package org.fractalx.runtime;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.concurrent.TimeUnit;

/**
 * Client-side correlation ID propagation.
 *
 * <p>Wraps the {@code NetScopeChannelFactory} bean with a CGLIB proxy so that every
 * {@code channelFor()} call returns a {@link ManagedChannel} that intercepts
 * {@code newCall()} to inject {@code x-correlation-id} gRPC metadata via
 * {@link NetScopeContextInterceptor}.
 *
 * <p>{@code NetScopeChannelFactory} is detected by class name rather than a direct type
 * reference to avoid a compile-time dependency on the netscope-client JAR (which may be
 * compiled with a newer JDK than this module targets).  Runtime behaviour is unchanged.
 *
 * <p><b>Why NOT {@code ClientInterceptors.intercept()}?</b><br>
 * {@code ClientInterceptors.intercept(channel, interceptor)} returns an
 * {@code InterceptorChannel} which implements {@code Channel} but NOT
 * {@code ManagedChannel}.  {@code NetScopeTemplate.server()} casts the
 * {@code channelFor()} result to {@code ManagedChannel} → instant
 * {@code ClassCastException}.  Instead we create a thin {@link ManagedChannel}
 * delegating wrapper whose {@code newCall()} passes through the interceptor,
 * so the returned type stays {@code ManagedChannel} throughout.
 */
@ConditionalOnClass(name = "org.fractalx.netscope.client.core.NetScopeChannelFactory")
public class NetScopeGrpcInterceptorConfigurer implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(NetScopeGrpcInterceptorConfigurer.class);
    private static final String CHANNEL_FACTORY_CLASS =
            "org.fractalx.netscope.client.core.NetScopeChannelFactory";

    private final NetScopeContextInterceptor correlationInterceptor;

    public NetScopeGrpcInterceptorConfigurer(NetScopeContextInterceptor correlationInterceptor) {
        this.correlationInterceptor = correlationInterceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (isNetScopeChannelFactory(bean)) {
            return wrapChannelFactory(bean);
        }
        return bean;
    }

    /**
     * Checks whether {@code bean} is (or extends) {@code NetScopeChannelFactory} using
     * class-name comparison so no compile-time import of that class is needed.
     */
    private boolean isNetScopeChannelFactory(Object bean) {
        if (bean == null) return false;
        Class<?> cls = bean.getClass();
        while (cls != null) {
            if (CHANNEL_FACTORY_CLASS.equals(cls.getName())) return true;
            cls = cls.getSuperclass();
        }
        return false;
    }

    private Object wrapChannelFactory(Object factory) {
        ProxyFactory pf = new ProxyFactory(factory);
        pf.setProxyTargetClass(true);
        pf.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().startsWith("channelFor")
                    && result instanceof ManagedChannel channel) {
                // Return a ManagedChannel wrapper (NOT InterceptorChannel) so that
                // NetScopeTemplate's ManagedChannel cast succeeds.
                return interceptingManagedChannel(channel);
            }
            return result;
        });
        log.info("NetScopeGrpcInterceptorConfigurer: correlation ClientInterceptor wired into NetScopeChannelFactory");
        return pf.getProxy();
    }

    /**
     * Creates a {@link ManagedChannel} that delegates every method to {@code delegate}
     * but intercepts {@code newCall()} to run {@link NetScopeContextInterceptor} first,
     * injecting {@code x-correlation-id} into outgoing gRPC metadata.
     */
    private ManagedChannel interceptingManagedChannel(ManagedChannel delegate) {
        return new ManagedChannel() {

            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                return correlationInterceptor.interceptCall(method, callOptions, delegate);
            }

            @Override public String authority()    { return delegate.authority(); }
            @Override public ManagedChannel shutdown()    { return delegate.shutdown(); }
            @Override public boolean isShutdown()         { return delegate.isShutdown(); }
            @Override public boolean isTerminated()       { return delegate.isTerminated(); }
            @Override public ManagedChannel shutdownNow() { return delegate.shutdownNow(); }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit)
                    throws InterruptedException { return delegate.awaitTermination(timeout, unit); }
        };
    }
}
