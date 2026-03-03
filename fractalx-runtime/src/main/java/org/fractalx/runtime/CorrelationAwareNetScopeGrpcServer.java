package org.fractalx.runtime;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.fractalx.netscope.server.config.NetScopeConfig;
import org.fractalx.netscope.server.grpc.NetScopeAuthInterceptor;
import org.fractalx.netscope.server.grpc.NetScopeGrpcServer;
import org.fractalx.netscope.server.grpc.NetScopeGrpcServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * Drop-in replacement for {@link NetScopeGrpcServer} that adds
 * {@link NetScopeContextInterceptor} as a second {@code ServerInterceptor}.
 *
 * <p>This class is registered via {@link NetScopeGrpcServerBeanOverrider} which replaces
 * the {@code netScopeGrpcServer} bean definition before any beans are instantiated.
 * The override is transparent: all server configuration ({@code keepAliveTime},
 * {@code maxInboundMessageSize}, etc.) is still read from {@link NetScopeConfig} —
 * nothing is hardcoded here.
 *
 * <p>Because {@link NetScopeGrpcServer#start()} is annotated with {@code @PostConstruct}
 * and this class overrides it, Spring dispatches the {@code @PostConstruct} call via
 * polymorphism to this implementation.  We rebuild the gRPC server with both
 * {@link NetScopeAuthInterceptor} AND our correlation interceptor, then store the
 * result in the parent's {@code server} field via reflection so that
 * {@link NetScopeGrpcServer#stop()} works unchanged.
 */
public class CorrelationAwareNetScopeGrpcServer extends NetScopeGrpcServer {

    private static final Logger log = LoggerFactory.getLogger(CorrelationAwareNetScopeGrpcServer.class);

    private final NetScopeContextInterceptor correlationInterceptor;

    public CorrelationAwareNetScopeGrpcServer(
            NetScopeConfig config,
            NetScopeGrpcServiceImpl grpcService,
            NetScopeContextInterceptor correlationInterceptor) {
        super(config, grpcService);
        this.correlationInterceptor = correlationInterceptor;
    }

    /**
     * Replaces {@link NetScopeGrpcServer#start()}.  Reads config and service from
     * the parent's private final fields via reflection, builds the gRPC server with
     * both the original auth interceptor and our correlation interceptor, then stores
     * the running {@link Server} back in the parent's {@code server} field.
     */
    @Override
    public void start() throws IOException {
        try {
            NetScopeConfig config = getField("config", NetScopeConfig.class);
            NetScopeGrpcServiceImpl grpcService = getField("grpcService", NetScopeGrpcServiceImpl.class);

            NetScopeConfig.GrpcConfig grpcCfg = config.getGrpc();

            if (!grpcCfg.isEnabled()) {
                log.info("NetScope gRPC server is disabled");
                return;
            }

            ServerBuilder<?> builder = ServerBuilder.forPort(grpcCfg.getPort())
                    .addService(grpcService)
                    .intercept(new NetScopeAuthInterceptor())   // original auth interceptor
                    .intercept(correlationInterceptor)          // correlation ID propagation
                    .maxInboundMessageSize(grpcCfg.getMaxInboundMessageSize());

            // Optional gRPC reflection service (same conditional as original start())
            if (grpcCfg.isEnableReflection()) {
                tryAddReflectionService(builder);
            }

            if (grpcCfg.getKeepAliveTime() > 0) {
                builder.keepAliveTime(grpcCfg.getKeepAliveTime(), TimeUnit.SECONDS);
            }
            if (grpcCfg.getKeepAliveTimeout() > 0) {
                builder.keepAliveTimeout(grpcCfg.getKeepAliveTimeout(), TimeUnit.SECONDS);
            }
            builder.permitKeepAliveWithoutCalls(grpcCfg.isPermitKeepAliveWithoutCalls());
            if (grpcCfg.getMaxConnectionIdle() > 0) {
                builder.maxConnectionIdle(grpcCfg.getMaxConnectionIdle(), TimeUnit.SECONDS);
            }
            if (grpcCfg.getMaxConnectionAge() > 0) {
                builder.maxConnectionAge(grpcCfg.getMaxConnectionAge(), TimeUnit.SECONDS);
            }

            Server server = builder.build().start();

            // Store in parent's `server` field so stop() works correctly
            setField("server", server);

            log.info("CorrelationAwareNetScopeGrpcServer started on port {} with correlation interceptor",
                    grpcCfg.getPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    stop();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("CorrelationAwareNetScopeGrpcServer: failed to start", e);
        }
    }

    // ------------------------------------------------------------------
    // Reflection helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T getField(String name, Class<T> type) throws ReflectiveOperationException {
        Field f = NetScopeGrpcServer.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(this);
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        Field f = NetScopeGrpcServer.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(this, value);
    }

    private void tryAddReflectionService(ServerBuilder<?> builder) {
        // grpc-services 1.75.0 ships ProtoReflectionService (v1alpha) and ProtoReflectionServiceV1
        String[] candidates = {
                "io.grpc.protobuf.services.ProtoReflectionServiceV1",
                "io.grpc.protobuf.services.ProtoReflectionService"
        };
        for (String cls : candidates) {
            try {
                Object svc = Class.forName(cls).getMethod("newInstance").invoke(null);
                builder.addService((BindableService) svc);
                return;
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        log.warn("CorrelationAwareNetScopeGrpcServer: could not add gRPC reflection service (class not found)");
    }
}
