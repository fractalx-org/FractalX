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

    /**
     * Explicit cross-module dependencies: the bean types injected from other modules.
     *
     * <p>Declaring these explicitly makes decomposition reliable for any naming convention.
     * When omitted, FractalX falls back to heuristic detection (types ending in "Service"
     * or "Client"), which only works if the monolith follows that naming pattern.
     *
     * <p>Example:
     * <pre>
     * {@code @DecomposableModule(serviceName="order-service", port=8081,
     *                   dependencies={PaymentProcessor.class, InventoryManager.class})}
     * </pre>
     */
    Class<?>[] dependencies() default {};
}