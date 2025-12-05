package com.fractalx.core.generator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ensures all necessary imports are present in generated/copied files
 */
public class ImportPreserver {

    private static final Logger log = LoggerFactory.getLogger(ImportPreserver.class);
    private final JavaParser javaParser;

    // Common Spring imports that should always be present if annotations are used
    private static final Set<String> REQUIRED_SPRING_IMPORTS = Set.of(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.RequestBody",
            "org.springframework.web.bind.annotation.RequestParam"
    );

    public ImportPreserver() {
        this.javaParser = new JavaParser();
    }

    /**
     * Ensure all necessary imports are present in service files
     */
    public void ensureImports(Path serviceRoot) throws IOException {
        log.debug("Ensuring imports in: {}", serviceRoot);

        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("Application.java")) // Skip application class
                    .forEach(this::checkAndAddImports);
        }
    }

    private void checkAndAddImports(Path javaFile) {
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            String content = cu.toString();
            Set<String> missingImports = new HashSet<>();

            // Check which imports are needed based on annotations/classes used
            for (String requiredImport : REQUIRED_SPRING_IMPORTS) {
                String annotationOrClass = requiredImport.substring(requiredImport.lastIndexOf('.') + 1);

                // Check if annotation/class is used but import is missing
                if (content.contains("@" + annotationOrClass) || content.contains(annotationOrClass + " ")) {
                    boolean hasImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals(requiredImport));

                    if (!hasImport) {
                        missingImports.add(requiredImport);
                    }
                }
            }

            // Add missing imports
            if (!missingImports.isEmpty()) {
                NodeList<ImportDeclaration> imports = cu.getImports();
                for (String importToAdd : missingImports) {
                    imports.add(new ImportDeclaration(importToAdd, false, false));
                    log.debug("Added missing import: {} to {}", importToAdd, javaFile.getFileName());
                }

                Files.writeString(javaFile, cu.toString());
            }

        } catch (IOException e) {
            log.error("Failed to check imports in: " + javaFile, e);
        }
    }
}