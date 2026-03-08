package org.fractalx.runtime;

import io.grpc.*;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Propagates the FractalX correlation ID across gRPC/NetScope calls.
 *
 * Client side: reads correlationId from MDC and injects it into outgoing gRPC metadata.
 * Server side: extracts correlationId from incoming gRPC metadata and populates MDC
 *              via ForwardingServerCallListener so it is available in the gRPC thread
 *              that actually invokes the service method (onHalfClose / onMessage).
 */
@Component
@ConditionalOnClass(name = "io.grpc.BindableService")
public class NetScopeContextInterceptor implements ClientInterceptor, ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(NetScopeContextInterceptor.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final Metadata.Key<String> CORRELATION_METADATA_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Autowired(required = false)
    private Tracer tracer;

    // ---- Client side: inject correlationId into outgoing gRPC metadata ----

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String correlationId = MDC.get(CORRELATION_ID_KEY);
                if (correlationId != null) {
                    headers.put(CORRELATION_METADATA_KEY, correlationId);
                    log.debug("NetScope: injected correlationId={} into outgoing gRPC metadata", correlationId);
                }
                super.start(responseListener, headers);
            }
        };
    }

    // ---- Server side: extract correlationId from metadata, propagate via listener ----

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String correlationId = headers.get(CORRELATION_METADATA_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            log.debug("NetScope: no correlationId in gRPC metadata, generated {}", correlationId);
        } else {
            log.debug("NetScope: extracted correlationId={} from gRPC metadata", correlationId);
        }

        final String cid = correlationId;
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        // Wrap the listener so MDC is set in each callback where the service method may run.
        // For unary calls, invokeMethod() is triggered inside onHalfClose().
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {

            private void tagCurrentSpan(String cid) {
                if (tracer != null) {
                    io.micrometer.tracing.Span span = tracer.currentSpan();
                    if (span != null) span.tag(CORRELATION_ID_KEY, cid);
                }
            }

            @Override
            public void onMessage(ReqT message) {
                MDC.put(CORRELATION_ID_KEY, cid);
                try {
                    super.onMessage(message);
                } finally {
                    MDC.remove(CORRELATION_ID_KEY);
                }
            }

            @Override
            public void onHalfClose() {
                MDC.put(CORRELATION_ID_KEY, cid);
                tagCurrentSpan(cid);
                try {
                    super.onHalfClose();
                } finally {
                    MDC.remove(CORRELATION_ID_KEY);
                }
            }

            @Override
            public void onComplete() {
                MDC.remove(CORRELATION_ID_KEY);
                super.onComplete();
            }

            @Override
            public void onCancel() {
                MDC.remove(CORRELATION_ID_KEY);
                super.onCancel();
            }
        };
    }
}
