package org.fractalx.annotations;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DecomposableModule {
    /**
     * Logical name of the service when decomposed
     */
    String serviceName();

    /**
     * Whether this module should be independently deployable
     */
    boolean independentDeployment() default true;

    /**
     * Port for the service (0 = auto-assign)
     */
    int port() default 0;

    /**
     * Database schema ownership
     */
    String[] ownedSchemas() default {};
}