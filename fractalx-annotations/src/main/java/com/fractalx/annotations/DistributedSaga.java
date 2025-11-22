package com.fractalx.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedSaga {
    /**
     * Saga identifier
     */
    String sagaId();

    /**
     * Compensation method name for rollback
     */
    String compensationMethod() default "";

    /**
     * Timeout in milliseconds
     */
    long timeout() default 30000;
}