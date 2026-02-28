package org.fractalx.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedSaga {
    /**
     * Unique saga identifier used to correlate orchestration state.
     * Example: {@code "place-order-saga"}
     */
    String sagaId();

    /**
     * Name of the compensation (rollback) method to call on failure.
     * The method must be in the same class and accept the same parameters.
     */
    String compensationMethod() default "";

    /**
     * Timeout in milliseconds for the entire saga execution.
     */
    long timeout() default 30000;

    /**
     * Human-readable description of what this saga does.
     * Used in generated documentation and the admin UI.
     */
    String description() default "";
}