package org.fractalx.core.generator.transformation;

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
 * Removes FractalX-specific annotations and their imports from copied source files.
 *
 * <p>This step is concerned only with annotation removal. File deletion (e.g. removing
 * monolith-only implementation classes) is handled separately by {@link FileCleanupStep}.
 */
public class AnnotationRemover {

    private static final Logger log = LoggerFactory.getLogger(AnnotationRemover.class);

    private static final List<String> FRACTALX_ANNOTATIONS = List.of(
            "DecomposableModule",
            "ServiceBoundary",
            "DistributedSaga"
    );

    private static final List<String> FRACTALX_IMPORT_PREFIXES = List.of(
            "org.fractalx.annotations"
    );

    private final JavaParser javaParser;

    public AnnotationRemover() {
        this.javaParser = new JavaParser();
    }

    public void processServiceDirectory(Path serviceRoot) throws IOException {
        log.debug("Removing FractalX annotations in: {}", serviceRoot);

        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(this::processFile);
        }
    }

    private void processFile(Path javaFile) {
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            boolean modified = removeAnnotations(cu) | removeFractalXImports(cu);

            if (modified) {
                Files.writeString(javaFile, cu.toString());
                log.debug("Cleaned annotations from: {}", javaFile.getFileName());
            }
        } catch (IOException e) {
            log.error("Failed to process file: {}", javaFile, e);
        }
    }

    private boolean removeAnnotations(CompilationUnit cu) {
        boolean modified = false;
        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            List<AnnotationExpr> toRemove = new ArrayList<>();
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                if (FRACTALX_ANNOTATIONS.contains(annotation.getNameAsString())) {
                    toRemove.add(annotation);
                }
            }
            for (AnnotationExpr annotation : toRemove) {
                if (classDecl.remove(annotation)) {
                    log.debug("Removed @{} from {}", annotation.getNameAsString(), classDecl.getNameAsString());
                    modified = true;
                }
            }
        }
        return modified;
    }

    private boolean removeFractalXImports(CompilationUnit cu) {
        boolean modified = false;
        List<ImportDeclaration> toRemove = new ArrayList<>();

        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            for (String prefix : FRACTALX_IMPORT_PREFIXES) {
                if (name.startsWith(prefix)) {
                    toRemove.add(imp);
                    break;
                }
            }
        }

        for (ImportDeclaration imp : toRemove) {
            if (cu.remove(imp)) {
                log.debug("Removed import: {}", imp.getNameAsString());
                modified = true;
            }
        }
        return modified;
    }
}
