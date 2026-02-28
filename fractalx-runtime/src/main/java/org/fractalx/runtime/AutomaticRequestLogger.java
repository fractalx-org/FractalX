package org.fractalx.runtime;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC; // Added for explicit Trace ID visibility
import org.springframework.stereotype.Component;
import java.util.Arrays;

/**
 * Zero-Code Observability
 * Automatically logs input/output for all Controllers in generated services.
 */
@Aspect
@Component
public class AutomaticRequestLogger {

    private static final Logger log = LoggerFactory.getLogger(AutomaticRequestLogger.class);

    // Target any class annotated with @RestController
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {}

    @Before("controllerMethods()")
    public void logRequest(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        String correlationId = MDC.get("correlationId"); // Capture Trace ID explicitly

        log.info("[AUTO-LOG] [CorrelationId:{}] Entering {}.{}() with args: {}",
                correlationId != null ? correlationId : "N/A",
                className,
                methodName,
                Arrays.toString(args));
    }

    @AfterReturning(pointcut = "controllerMethods()", returning = "result")
    public void logResponse(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String correlationId = MDC.get("correlationId");

        log.info("[AUTO-LOG] [CorrelationId:{}] Exiting {}.{}() returned: {}",
                correlationId != null ? correlationId : "N/A",
                className,
                methodName,
                result);
    }
}