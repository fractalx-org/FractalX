package com.fractalx.core.generator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Removes FractalX-specific annotations using JavaParser AST manipulation
 */
public class AnnotationRemover {

    private static final Logger log = LoggerFactory.getLogger(AnnotationRemover.class);
    private final JavaParser javaParser;

    // List of FractalX annotations to remove
    private static final List<String> FRACTALX_ANNOTATIONS = List.of(
            "DecomposableModule",
            "ServiceBoundary",
            "DistributedSaga"
    );

    // List of FractalX packages to remove from imports
    private static final List<String> FRACTALX_IMPORT_PATTERNS = List.of(
            "com.fractalx.annotations",
            "com.fractalx.testapp.payment"  // Cross-module imports
    );

    public AnnotationRemover() {
        this.javaParser = new JavaParser();
    }

    /**
     * Process all Java files in the service directory
     */
    public void processServiceDirectory(Path serviceRoot) throws IOException {
        log.info("Processing service directory: {}", serviceRoot);

        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::processJavaFile);
        }
    }

    /**
     * Process a single Java file using AST
     */
    private void processJavaFile(Path javaFile) {
        try {
            log.debug("Processing file: {}", javaFile.getFileName());

            // Parse the Java file
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            boolean modified = false;

            // Step 1: Remove FractalX annotations from classes
            modified |= removeAnnotationsFromClasses(cu);

            // Step 2: Remove FractalX imports
            modified |= removeUnusedImports(cu);

            // Step 3: Handle special cases (like PaymentClientImpl)
            if (javaFile.toString().contains("PaymentClientImpl.java")) {
                log.info("Removing file: {}", javaFile.getFileName());
                Files.delete(javaFile);
                return;
            }

            // Only write back if changes were made
            if (modified) {
                Files.writeString(javaFile, cu.toString());
                log.debug("✓ Transformed: {}", javaFile.getFileName());
            }

        } catch (IOException e) {
            log.error("Failed to process file: " + javaFile, e);
        }
    }

    /**
     * Remove FractalX annotations from all classes in the compilation unit
     */
    private boolean removeAnnotationsFromClasses(CompilationUnit cu) {
        boolean modified = false;

        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration classDecl : classes) {
            List<AnnotationExpr> annotationsToRemove = new ArrayList<>();

            // Find all FractalX annotations
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();

                if (FRACTALX_ANNOTATIONS.contains(annotationName)) {
                    annotationsToRemove.add(annotation);
                    log.debug("Found annotation to remove: @{}", annotationName);
                }
            }

            // Remove the annotations
            for (AnnotationExpr annotation : annotationsToRemove) {
                boolean removed = classDecl.remove(annotation);
                if (removed) {
                    log.debug("Removed @{} from {}",
                            annotation.getNameAsString(),
                            classDecl.getNameAsString());
                    modified = true;
                }
            }
        }

        return modified;
    }

    /**
     * Remove unused FractalX imports
     */
    private boolean removeUnusedImports(CompilationUnit cu) {
        boolean modified = false;

        List<ImportDeclaration> importsToRemove = new ArrayList<>();

        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();

            // Check if this is a FractalX import
            for (String pattern : FRACTALX_IMPORT_PATTERNS) {
                if (importName.startsWith(pattern)) {
                    importsToRemove.add(importDecl);
                    log.debug("Found import to remove: {}", importName);
                    break;
                }
            }
        }

        // Remove the imports
        for (ImportDeclaration importDecl : importsToRemove) {
            boolean removed = cu.remove(importDecl);
            if (removed) {
                log.debug("Removed import: {}", importDecl.getNameAsString());
                modified = true;
            }
        }

        return modified;
    }
}