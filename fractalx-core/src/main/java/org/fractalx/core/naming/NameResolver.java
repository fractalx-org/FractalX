package org.fractalx.core.naming;

import org.fractalx.core.model.FractalModule;

import java.util.List;

/**
 * Single source of truth for all name-derivation logic in the FractalX generation pipeline.
 *
 * <p>Every pipeline step should call methods here instead of maintaining its own
 * conversion logic, eliminating naming divergence across generators.
 *
 * <p>All behaviour is driven by the {@link NamingConventions} record, which is
 * configurable via {@code fractalx-config.yml}. Call {@link #defaults()} to get
 * an instance backed by the original hardcoded values.
 */
public final class NameResolver {

    private final NamingConventions conventions;
    private final EnglishPluralizer pluralizer;

    public NameResolver(NamingConventions conventions) {
        this.conventions = conventions;
        this.pluralizer  = new EnglishPluralizer(conventions.irregularPlurals());
    }

    /** Returns a resolver backed by {@link NamingConventions#defaults()}. */
    public static NameResolver defaults() {
        return new NameResolver(NamingConventions.defaults());
    }

    // ── Bean-type → service-name ──────────────────────────────────────────────

    /**
     * Converts a Java bean type name (CamelCase) to a kebab-case service name.
     *
     * <pre>
     * "PaymentService"  → "payment-service"
     * "Svc1Service"     → "svc1-service"
     * "OrderClient"     → "order-client"
     * </pre>
     */
    public String beanTypeToServiceName(String beanType) {
        if (beanType == null || beanType.isBlank()) return beanType;
        return beanType
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase();
    }

    // ── Module lookup (exact → normalized) ───────────────────────────────────

    /**
     * Finds the module whose service name matches {@code derivedServiceName}.
     * Uses an exact match first; falls back to normalized matching (strips separators
     * and configured suffixes) if no exact match is found.
     *
     * @param derivedServiceName  the name derived by {@link #beanTypeToServiceName}
     * @param modules             all known modules in the current generation run
     * @return the matched module, or {@code null} if none matches
     */
    public FractalModule findModule(String derivedServiceName, List<FractalModule> modules) {
        if (derivedServiceName == null || modules == null || modules.isEmpty()) return null;

        // Pass 1: exact match (respecting caseInsensitiveServiceNames flag)
        for (FractalModule m : modules) {
            boolean match = conventions.caseInsensitiveServiceNames()
                    ? derivedServiceName.equalsIgnoreCase(m.getServiceName())
                    : derivedServiceName.equals(m.getServiceName());
            if (match) return m;
        }

        // Pass 2: normalized match (always case-insensitive, strips separators + suffixes)
        String normalizedDerived = normalizeModuleName(derivedServiceName);
        for (FractalModule m : modules) {
            if (normalizedDerived.equals(normalizeModuleName(m.getServiceName()))) return m;
        }

        return null;
    }

    /**
     * Normalizes a module / service name for fuzzy matching:
     * lower-cases, strips all non-alphanumeric characters, then strips every
     * configured suffix from {@link NamingConventions#aggregateClassSuffixes()}.
     *
     * <pre>
     * "order-service"  → "order"
     * "OrderService"   → "order"   (after lowercase + separator removal)
     * "svc1-service"   → "svc1"
     * "svc1"           → "svc1"
     * </pre>
     */
    public String normalizeModuleName(String name) {
        if (name == null) return null;
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        // Iteratively strip configured suffixes (handles stacked suffixes like "ServiceClient")
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : conventions.aggregateClassSuffixes()) {
                String lower = suffix.toLowerCase();
                if (normalized.endsWith(lower) && normalized.length() > lower.length()) {
                    normalized = normalized.substring(0, normalized.length() - lower.length());
                    changed = true;
                    break;
                }
            }
        }
        return normalized;
    }

    // ── Class / interface name derivation ─────────────────────────────────────

    /**
     * Converts a kebab-case (or hyphen-separated) service name to PascalCase.
     *
     * <pre>
     * "order-service" → "OrderService"
     * "svc1"          → "Svc1"
     * </pre>
     */
    public String toPascalCase(String kebabCase) {
        if (kebabCase == null || kebabCase.isBlank()) return kebabCase;
        StringBuilder sb = new StringBuilder();
        for (String part : kebabCase.split("[-_]")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Returns the generated {@code *Client} interface name for a dependency bean type.
     *
     * <pre>
     * "PaymentService" → "PaymentServiceClient"
     * "Svc1Service"    → "Svc1ServiceClient"
     * </pre>
     */
    public String toClientInterfaceName(String beanType) {
        return beanType + "Client";
    }

    /**
     * Strips the first matching {@link NamingConventions#aggregateClassSuffixes() aggregate suffix}
     * from a class name to derive its domain aggregate name.
     *
     * <pre>
     * "OrderService" → "Order"   (when "Service" is in aggregateClassSuffixes)
     * "OrderFacade"  → "Order"   (when "Facade"  is in aggregateClassSuffixes)
     * "OrderModule"  → "Order"
     * </pre>
     */
    public String toAggregateName(String className) {
        if (className == null) return null;
        for (String suffix : conventions.aggregateClassSuffixes()) {
            if (className.endsWith(suffix) && className.length() > suffix.length()) {
                return className.substring(0, className.length() - suffix.length());
            }
        }
        return className;
    }

    // ── Compensation method candidates ────────────────────────────────────────

    /**
     * Returns all candidate compensation method names for a given forward method name.
     *
     * <pre>
     * "processPayment" → ["cancelProcessPayment", "rollbackProcessPayment",
     *                      "undoProcessPayment",  "revertProcessPayment", ...]
     * </pre>
     */
    public List<String> compensationCandidatesFor(String forwardMethod) {
        if (forwardMethod == null || forwardMethod.isBlank()) return List.of();
        String capitalized = Character.toUpperCase(forwardMethod.charAt(0)) + forwardMethod.substring(1);
        return conventions.compensationPrefixes().stream()
                .map(prefix -> prefix + capitalized)
                .toList();
    }

    // ── Type classification ────────────────────────────────────────────────────

    /** Returns {@code true} if the class name ends with a configured infrastructure suffix. */
    public boolean isInfrastructureClass(String className) {
        if (className == null) return false;
        for (String suffix : conventions.infrastructureSuffixes()) {
            if (className.endsWith(suffix)) return true;
        }
        return false;
    }

    /** Returns {@code true} if the type name indicates a cross-module dependency. */
    public boolean isDependencyType(String typeName) {
        if (typeName == null) return false;
        for (String suffix : conventions.dependencyTypeSuffixes()) {
            if (typeName.endsWith(suffix)) return true;
        }
        return false;
    }

    /** Returns {@code true} if the method name is a recognized event publisher. */
    public boolean isEventPublisherMethod(String methodName) {
        return conventions.eventPublisherMethodNames().contains(methodName);
    }

    // ── Pluralization delegates ────────────────────────────────────────────────

    /** Singularizes an English word using configured irregular plurals + morphology rules. */
    public String singularize(String word) { return pluralizer.singularize(word); }

    /** Pluralizes an English word using configured irregular plurals + morphology rules. */
    public String pluralize(String word)   { return pluralizer.pluralize(word); }

    // ── Accessor ──────────────────────────────────────────────────────────────

    public NamingConventions conventions() { return conventions; }
}
