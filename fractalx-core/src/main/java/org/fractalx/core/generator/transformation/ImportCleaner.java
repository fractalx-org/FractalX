package org.fractalx.core.generator.transformation;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
 * Removes unused imports from generated/copied Java files.
 * Spring Framework imports are kept conservatively to avoid breaking auto-configuration.
 */
public class ImportCleaner implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ImportCleaner.class);

    private final JavaParser javaParser;

    public ImportCleaner() {
        this.javaParser = new JavaParser();
    }

    @Override
    public void generate(GenerationContext context) throws IOException {
        log.debug("Cleaning imports in: {}", context.getServiceRoot());
        try (Stream<Path> paths = Files.walk(context.getServiceRoot())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(this::cleanFile);
        }
    }

    private void cleanFile(Path javaFile) {
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            Set<String> usedTypes       = collectUsedTypes(cu);
            Set<String> usedAnnotations = collectUsedAnnotations(cu);

            boolean modified = false;
            Set<ImportDeclaration> toRemove = new HashSet<>();

            for (ImportDeclaration imp : cu.getImports()) {
                if (imp.isAsterisk()) continue;

                String importName    = imp.getNameAsString();
                String importedClass = lastSegment(importName);

                boolean used = usedTypes.contains(importedClass)
                        || usedAnnotations.contains(importedClass)
                        || importName.startsWith("org.springframework.");

                if (!used) {
                    toRemove.add(imp);
                    modified = true;
                }
            }

            for (ImportDeclaration imp : toRemove) {
                cu.remove(imp);
                log.debug("Removed unused import: {}", imp.getNameAsString());
            }

            if (modified) {
                Files.writeString(javaFile, cu.toString());
            }
        } catch (IOException e) {
            log.error("Failed to clean imports in: {}", javaFile, e);
        }
    }

    private Set<String> collectUsedTypes(CompilationUnit cu) {
        Set<String> used = new HashSet<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
            c.getExtendedTypes().forEach(t -> used.add(t.getNameAsString()));
            c.getImplementedTypes().forEach(t -> used.add(t.getNameAsString()));
        });
        cu.findAll(FieldDeclaration.class).forEach(f ->
                f.getCommonType().ifClassOrInterfaceType(t -> used.add(t.getNameAsString())));
        cu.findAll(MethodDeclaration.class).forEach(m -> {
            m.getType().ifClassOrInterfaceType(t -> used.add(t.getNameAsString()));
            m.getParameters().forEach(p ->
                    p.getType().ifClassOrInterfaceType(t -> used.add(t.getNameAsString())));
        });
        cu.findAll(ClassOrInterfaceType.class).forEach(t -> used.add(t.getNameAsString()));

        // Capture static call receivers like LoggerFactory.getLogger(...), LocalDate.now(), etc.
        // These parse as NameExpr (not ClassOrInterfaceType), so they'd be missed otherwise.
        cu.findAll(NameExpr.class).forEach(n -> {
            String name = n.getNameAsString();
            if (Character.isUpperCase(name.charAt(0))) {
                used.add(name);
            }
        });

        return used;
    }

    private Set<String> collectUsedAnnotations(CompilationUnit cu) {
        Set<String> used = new HashSet<>();
        cu.findAll(AnnotationExpr.class).forEach(a -> used.add(a.getNameAsString()));
        return used;
    }

    private String lastSegment(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
