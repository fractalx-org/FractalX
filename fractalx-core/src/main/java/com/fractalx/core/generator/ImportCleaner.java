package com.fractalx.core.generator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Cleans unused imports after annotation removal
 */
public class ImportCleaner {

    private static final Logger log = LoggerFactory.getLogger(ImportCleaner.class);
    private final JavaParser javaParser;

    public ImportCleaner() {
        this.javaParser = new JavaParser();
    }

    /**
     * Clean unused imports in all Java files
     */
    public void cleanImports(Path serviceRoot) throws IOException {
        log.debug("Cleaning imports in: {}", serviceRoot);

        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::cleanImportsInFile);
        }
    }

    /**
     * Clean unused imports in a single file
     */
    private void cleanImportsInFile(Path javaFile) {
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            // Collect all types used in the file
            Set<String> usedTypes = collectUsedTypes(cu);

            // Collect all annotations used in the file
            Set<String> usedAnnotations = collectUsedAnnotations(cu);

            // Remove unused imports
            boolean modified = false;
            Set<ImportDeclaration> importsToRemove = new HashSet<>();

            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.isAsterisk()) {
                    continue; // Keep wildcard imports
                }

                String importName = importDecl.getNameAsString();
                String importedClass = extractClassName(importName);

                // Don't remove if:
                // 1. The type is used in the code
                // 2. The import is for an annotation that's being used
                // 3. It's a Spring Framework import (be conservative)
                boolean isUsed = usedTypes.contains(importedClass)
                        || usedAnnotations.contains(importedClass)
                        || isSpringImport(importName);

                if (!isUsed) {
                    importsToRemove.add(importDecl);
                    modified = true;
                }
            }

            // Remove unused imports
            for (ImportDeclaration importDecl : importsToRemove) {
                cu.remove(importDecl);
                log.debug("Removed unused import: {}", importDecl.getNameAsString());
            }

            if (modified) {
                Files.writeString(javaFile, cu.toString());
            }

        } catch (IOException e) {
            log.error("Failed to clean imports in: " + javaFile, e);
        }
    }

    /**
     * Collect all types actually used in the code
     */
    private Set<String> collectUsedTypes(CompilationUnit cu) {
        Set<String> usedTypes = new HashSet<>();

        // Collect from class declarations
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // Extended classes
            classDecl.getExtendedTypes().forEach(type ->
                    usedTypes.add(type.getNameAsString()));

            // Implemented interfaces
            classDecl.getImplementedTypes().forEach(type ->
                    usedTypes.add(type.getNameAsString()));
        });

        // Collect from field declarations
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            field.getCommonType().ifClassOrInterfaceType(type ->
                    usedTypes.add(type.getNameAsString()));
        });

        // Collect from method declarations
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            // Return type
            method.getType().ifClassOrInterfaceType(type ->
                    usedTypes.add(type.getNameAsString()));

            // Parameter types
            method.getParameters().forEach(param -> {
                param.getType().ifClassOrInterfaceType(type ->
                        usedTypes.add(type.getNameAsString()));
            });
        });

        // Collect from all type references in the code
        cu.findAll(ClassOrInterfaceType.class).forEach(type ->
                usedTypes.add(type.getNameAsString()));

        return usedTypes;
    }

    /**
     * Collect all annotations used in the code
     */
    private Set<String> collectUsedAnnotations(CompilationUnit cu) {
        Set<String> usedAnnotations = new HashSet<>();

        // Find all annotations in the file
        cu.findAll(AnnotationExpr.class).forEach(annotation -> {
            String annotationName = annotation.getNameAsString();
            usedAnnotations.add(annotationName);
        });

        return usedAnnotations;
    }

    /**
     * Check if import is from Spring Framework (be conservative, keep these)
     */
    private boolean isSpringImport(String importName) {
        return importName.startsWith("org.springframework.");
    }

    /**
     * Extract simple class name from fully qualified name
     */
    private String extractClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
}