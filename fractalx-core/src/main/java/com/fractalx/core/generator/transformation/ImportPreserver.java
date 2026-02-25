package com.fractalx.core.generator.transformation;

import com.fractalx.core.generator.GenerationContext;
import com.fractalx.core.generator.ServiceFileGenerator;
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
 * Ensures commonly needed Spring imports are present in generated/copied files.
 * Compensates for any imports removed during annotation stripping.
 */
public class ImportPreserver implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ImportPreserver.class);

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

    private final JavaParser javaParser;

    public ImportPreserver() {
        this.javaParser = new JavaParser();
    }

    @Override
    public void generate(GenerationContext context) throws IOException {
        log.debug("Ensuring Spring imports in: {}", context.getServiceRoot());
        try (Stream<Path> paths = Files.walk(context.getServiceRoot())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("Application.java"))
                    .forEach(this::checkAndAddImports);
        }
    }

    private void checkAndAddImports(Path javaFile) {
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            String content = cu.toString();
            Set<String> missing = new HashSet<>();

            for (String required : REQUIRED_SPRING_IMPORTS) {
                String simpleName = lastSegment(required);
                boolean usedInCode = content.contains("@" + simpleName) || content.contains(simpleName + " ");
                boolean alreadyImported = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals(required));

                if (usedInCode && !alreadyImported) {
                    missing.add(required);
                }
            }

            if (!missing.isEmpty()) {
                NodeList<ImportDeclaration> imports = cu.getImports();
                for (String imp : missing) {
                    imports.add(new ImportDeclaration(imp, false, false));
                    log.debug("Added missing import: {} to {}", imp, javaFile.getFileName());
                }
                Files.writeString(javaFile, cu.toString());
            }
        } catch (IOException e) {
            log.error("Failed to preserve imports in: {}", javaFile, e);
        }
    }

    private String lastSegment(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
