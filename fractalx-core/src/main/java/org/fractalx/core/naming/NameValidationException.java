package org.fractalx.core.naming;

/**
 * Thrown by {@link NamingValidator} when a service dependency name cannot be
 * resolved to any known module and the configuration is therefore invalid.
 *
 * <p>In contrast to a warning-only run (the default), callers may opt into
 * strict mode by catching this exception and aborting the pipeline.
 */
public class NameValidationException extends RuntimeException {

    public NameValidationException(String message) {
        super(message);
    }
}
