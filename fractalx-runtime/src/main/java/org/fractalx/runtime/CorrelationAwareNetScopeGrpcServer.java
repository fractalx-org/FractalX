package org.fractalx.runtime;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Drop-in replacement for {@code NetScopeGrpcServer} that adds
 * {@link NetScopeContextInterceptor} as a second {@code ServerInterceptor}.
 *
 * <p>This class is registered via {@link NetScopeGrpcServerBeanOverrider} which replaces
 * the {@code netScopeGrpcServer} bean definition before any beans are instantiated.
 *
 * <p>All netscope classes are accessed via {@link ApplicationContext#getBean} and reflection
 * to avoid a compile-time dependency on netscope JARs — those JARs may be compiled with a
 * newer JDK than this module targets.  Runtime behaviour is identical to the original
 * {@code NetScopeGrpcServer}: all server configuration ({@code keepAliveTime},
 * {@code maxInboundMessageSize}, etc.) is read from {@code NetScopeConfig}, nothing is
 * hardcoded here.
 */
public class CorrelationAwareNetScopeGrpcServer implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(CorrelationAwareNetScopeGrpcServer.class);

    private final NetScopeContextInterceptor correlationInterceptor;
    private ApplicationContext applicationContext;
    private volatile Server server;

    public CorrelationAwareNetScopeGrpcServer(NetScopeContextInterceptor correlationInterceptor) {
        this.correlationInterceptor = correlationInterceptor;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    @PostConstruct
    public void start() throws IOException {
        try {
            Object config     = applicationContext.getBean(
                    Class.forName("org.fractalx.netscope.server.config.NetScopeConfig"));
            Object grpcService = applicationContext.getBean(
                    Class.forName("org.fractalx.netscope.server.grpc.NetScopeGrpcServiceImpl"));

            Object grpcCfg = invoke(config, "getGrpc");

            if (!boolOf(invoke(grpcCfg, "isEnabled"))) {
                log.info("NetScope gRPC server is disabled");
                return;
            }

            int  port        = intOf(invoke(grpcCfg,  "getPort"));
            int  maxMsgSize  = intOf(invoke(grpcCfg,  "getMaxInboundMessageSize"));
            boolean reflect  = boolOf(invoke(grpcCfg, "isEnableReflection"));
            long keepAlive   = longOf(invoke(grpcCfg, "getKeepAliveTime"));
            long keepAliveTo = longOf(invoke(grpcCfg, "getKeepAliveTimeout"));
            boolean permitKA = boolOf(invoke(grpcCfg, "isPermitKeepAliveWithoutCalls"));
            long maxIdle     = longOf(invoke(grpcCfg, "getMaxConnectionIdle"));
            long maxAge      = longOf(invoke(grpcCfg, "getMaxConnectionAge"));

            ServerInterceptor authInterceptor = (ServerInterceptor) Class
                    .forName("org.fractalx.netscope.server.grpc.NetScopeAuthInterceptor")
                    .getDeclaredConstructor()
                    .newInstance();

            ServerBuilder<?> builder = ServerBuilder.forPort(port)
                    .addService((BindableService) grpcService)
                    .intercept(authInterceptor)         // original auth interceptor
                    .intercept(correlationInterceptor)  // correlation ID propagation
                    .maxInboundMessageSize(maxMsgSize);

            if (reflect)       tryAddReflectionService(builder);
            if (keepAlive > 0) builder.keepAliveTime(keepAlive, TimeUnit.SECONDS);
            if (keepAliveTo > 0) builder.keepAliveTimeout(keepAliveTo, TimeUnit.SECONDS);
            builder.permitKeepAliveWithoutCalls(permitKA);
            if (maxIdle > 0)   builder.maxConnectionIdle(maxIdle, TimeUnit.SECONDS);
            if (maxAge > 0)    builder.maxConnectionAge(maxAge, TimeUnit.SECONDS);

            server = builder.build().start();

            log.info("CorrelationAwareNetScopeGrpcServer started on port {} with correlation interceptor", port);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("CorrelationAwareNetScopeGrpcServer: failed to start", e);
        }
    }

    @PreDestroy
    public void stop() {
        Server s = this.server;
        this.server = null;
        if (s != null && !s.isShutdown()) {
            s.shutdown();
            try {
                s.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---- Reflection helpers -----------------------------------------------

    private static Object invoke(Object obj, String method) throws ReflectiveOperationException {
        Method m = obj.getClass().getMethod(method);
        return m.invoke(obj);
    }

    private static boolean boolOf(Object v) { return (Boolean) v; }
    private static int     intOf(Object v)  { return ((Number) v).intValue(); }
    private static long    longOf(Object v) { return ((Number) v).longValue(); }

    private static void tryAddReflectionService(ServerBuilder<?> builder) {
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
