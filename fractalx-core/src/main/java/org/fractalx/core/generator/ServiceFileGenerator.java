package org.fractalx.core.generator;

import java.io.IOException;

/**
 * Strategy interface for a single step in the microservice generation pipeline.
 *
 * <p>Implementations receive a {@link GenerationContext} carrying the current module,
 * all modules, the source root, and the target service root. Each step is responsible
 * for generating exactly one concern (e.g. pom.xml, Application class, REST controller).
 *
 * <p>New generation steps can be added without modifying {@link ServiceGenerator}:
 * register them in its constructor.
 */
public interface ServiceFileGenerator {

    /**
     * Executes this generation step.
     *
     * @param context all inputs needed to generate files for the current module
     * @throws IOException if any file operation fails
     */
    void generate(GenerationContext context) throws IOException;
}
