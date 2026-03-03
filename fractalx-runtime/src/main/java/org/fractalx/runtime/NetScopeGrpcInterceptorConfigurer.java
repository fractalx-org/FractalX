package org.fractalx.runtime;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.fractalx.netscope.client.core.NetScopeChannelFactory;
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
 * <p>Wraps the {@link NetScopeChannelFactory} bean with a CGLIB proxy so that every
 * {@code channelFor()} call returns a {@link ManagedChannel} that intercepts
 * {@code newCall()} to inject {@code x-correlation-id} gRPC metadata via
 * {@link NetScopeContextInterceptor}.
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

    private final NetScopeContextInterceptor correlationInterceptor;

    public NetScopeGrpcInterceptorConfigurer(NetScopeContextInterceptor correlationInterceptor) {
        this.correlationInterceptor = correlationInterceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof NetScopeChannelFactory factory) {
            return wrapChannelFactory(factory);
        }
        return bean;
    }

    private NetScopeChannelFactory wrapChannelFactory(NetScopeChannelFactory factory) {
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
        return (NetScopeChannelFactory) pf.getProxy();
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
                // Apply the interceptor; `delegate` is the `next` channel in the chain
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
