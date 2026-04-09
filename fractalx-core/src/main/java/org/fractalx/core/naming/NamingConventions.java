package org.fractalx.core.naming;

import java.util.List;
import java.util.Map;

/**
 * Immutable configuration data model for all naming conventions used throughout
 * the FractalX generation pipeline.
 *
 * <p>Every value has a sensible default so the framework works without any
 * {@code naming} section in {@code fractalx-config.yml}. When a value is
 * explicitly set it overrides (or extends, for lists) the default.
 *
 * <p>Expected {@code fractalx-config.yml} structure:
 * <pre>
 * fractalx:
 *   naming:
 *     compensation-prefixes:    [cancel, rollback, undo, revert, release, refund]
 *     infrastructure-suffixes:  [Service, Repository, Controller, ...]
 *     dependency-type-suffixes: [Service, Client, Gateway, Bus, Processor]
 *     aggregate-class-suffixes: [Service, Module, Controller, Facade]
 *     event-publisher-method-names: [publishEvent, publish, emit, dispatch]
 *     case-insensitive-service-names: true
 *     irregular-plurals:
 *       child: children
 *       person: people
 * </pre>
 */
public record NamingConventions(

        /**
         * Prefixes checked when searching for a compensation/rollback method for a
         * saga step. e.g. a forward method {@code processPayment} is matched against
         * {@code cancelProcessPayment}, {@code rollbackProcessPayment}, etc.
         * <p>Configures: {@code fractalx.naming.compensation-prefixes}
         */
        List<String> compensationPrefixes,

        /**
         * Class name suffixes that mark a type as infrastructure. Classes with these
         * suffixes are never transitively copied into a consuming service as model
         * classes by {@code NetScopeClientGenerator}.
         * <p>Configures: {@code fractalx.naming.infrastructure-suffixes}
         */
        List<String> infrastructureSuffixes,

        /**
         * Field/parameter type suffixes that signal a cross-module dependency when
         * {@code ModuleAnalyzer} infers dependencies without an explicit declaration.
         * e.g. a field of type {@code PaymentGateway} is treated as a dep when
         * {@code "Gateway"} is in this list.
         * <p>Configures: {@code fractalx.naming.dependency-type-suffixes}
         */
        List<String> dependencyTypeSuffixes,

        /**
         * Class name suffixes stripped when deriving the aggregate domain name from
         * the saga owner class. e.g. {@code OrderService} → {@code Order},
         * {@code OrderFacade} → {@code Order} (when {@code "Facade"} is in this list).
         * <p>Configures: {@code fractalx.naming.aggregate-class-suffixes}
         */
        List<String> aggregateClassSuffixes,

        /**
         * Irregular English plural → singular mappings used when converting collection
         * field names to their ID counterparts.
         * e.g. {@code children} → {@code child} → {@code childIds}.
         * The map key is the plural form; the value is the singular.
         * <p>Configures: {@code fractalx.naming.irregular-plurals}
         */
        Map<String, String> irregularPlurals,

        /**
         * Method names recognized as Spring {@code ApplicationEvent} publishers when
         * scanning for event-driven patterns in {@code DecompositionHintsStep}.
         * <p>Configures: {@code fractalx.naming.event-publisher-method-names}
         */
        List<String> eventPublisherMethodNames,

        /**
         * When {@code true}, service name comparisons during module lookup are
         * case-insensitive. Enables {@code Order-Service} and {@code order-service}
         * to resolve to the same module.
         * <p>Configures: {@code fractalx.naming.case-insensitive-service-names}
         */
        boolean caseInsensitiveServiceNames

) {
    /**
     * Returns the canonical defaults that mirror the framework's original
     * hardcoded behaviour. Existing projects with no {@code naming} section
     * in {@code fractalx-config.yml} receive exactly these values.
     */
    public static NamingConventions defaults() {
        return new NamingConventions(
                List.of("cancel", "rollback", "undo", "revert", "release", "refund"),
                List.of("Service", "Repository", "Controller", "Module",
                        "Config", "Configuration", "Application"),
                List.of("Service", "Client"),
                List.of("Service", "Module"),
                Map.of(
                        "children", "child",
                        "people",   "person",
                        "data",     "datum",
                        "media",    "medium",
                        "criteria", "criterion",
                        "analyses", "analysis",
                        "theses",   "thesis",
                        "indices",  "index",
                        "vertices", "vertex",
                        "matrices", "matrix"
                ),
                List.of("publishEvent"),
                true
        );
    }
}
