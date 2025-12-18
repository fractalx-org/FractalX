package com.fractalx.annotations;

import java.lang.annotation.*;

/**
 * Marks that this application should have admin capabilities enabled
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminEnabled {
    /**
     * Admin service port
     */
    int port() default 9090;

    /**
     * Admin username
     */
    String username() default "admin";

    /**
     * Admin password (should be externalized in production)
     */
    String password() default "admin123";

    /**
     * Enable monitoring features
     */
    boolean monitoring() default true;

    /**
     * Enable logging aggregation
     */
    boolean logging() default true;
}