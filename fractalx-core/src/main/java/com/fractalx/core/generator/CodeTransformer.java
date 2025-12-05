package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Transforms copied code for microservice deployment
 */
public class CodeTransformer {

    private static final Logger log = LoggerFactory.getLogger(CodeTransformer.class);
    private final AnnotationRemover annotationRemover;
    private final ImportCleaner importCleaner;
    private final ImportPreserver importPreserver;

    public CodeTransformer() {
        this.annotationRemover = new AnnotationRemover();
        this.importCleaner = new ImportCleaner();
        this.importPreserver = new ImportPreserver();
    }

    /**
     * Transform all code in the service directory
     */
    public void transformCode(Path serviceRoot, FractalModule module) throws IOException {
        log.info("Transforming code for: {}", module.getServiceName());

        // Step 1: Remove FractalX annotations using AST
        annotationRemover.processServiceDirectory(serviceRoot);

        // Step 2: Ensure Spring imports are present
        importPreserver.ensureImports(serviceRoot);

        // Step 3: Clean up unused imports
        importCleaner.cleanImports(serviceRoot);

        log.info("✓ Code transformation complete for {}", module.getServiceName());
    }
}