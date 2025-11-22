package com.fractalx.annotations;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ServiceBoundary {
    /**
     * Defines what external modules can access this boundary
     */
    String[] allowedCallers() default {};

    /**
     * Whether to enforce strict boundary validation at build time
     */
    boolean strict() default true;
}