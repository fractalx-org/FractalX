package org.fractalx.core;

import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Analyzes source code to identify FractalX decomposable modules.
 */
public class ModuleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ModuleAnalyzer.class);
    private final JavaParser javaParser;

    public ModuleAnalyzer() {
        this.javaParser = new JavaParser();
    }

    /**
     * Analyzes a source directory and returns all decomposable modules found.
     */
    public List<FractalModule> analyzeProject(Path sourceRoot) throws IOException {
        List<FractalModule> modules = new ArrayList<>();

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        analyzeFile(path, modules);
                    } catch (IOException e) {
                        log.error("Failed to analyze file: {}", path, e);
                    }
                });

        return modules;
    }

    private void analyzeFile(Path filePath, List<FractalModule> modules) throws IOException {
        CompilationUnit cu = javaParser.parse(filePath).getResult()
                .orElseThrow(() -> new IOException("Failed to parse: " + filePath));

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            Optional<AnnotationExpr> annotation = classDecl.getAnnotationByName("DecomposableModule");
            if (annotation.isPresent()) {
                FractalModule module = extractModuleDescriptor(classDecl, annotation.get(), cu);
                modules.add(module);
                log.info("Found decomposable module: {}", module.getServiceName());
            }
        });
    }

    private FractalModule extractModuleDescriptor(
            ClassOrInterfaceDeclaration classDecl,
            AnnotationExpr annotation,
            CompilationUnit cu) {

        FractalModule.Builder builder = FractalModule.builder();
        builder.className(classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString()));

        cu.getPackageDeclaration().ifPresent(pd -> builder.packageName(pd.getNameAsString()));

        if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotation = annotation.asNormalAnnotationExpr();
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String name = pair.getNameAsString();
                String value = pair.getValue().toString().replace("\"", "");

                switch (name) {
                    case "serviceName" -> builder.serviceName(value);
                    case "port" -> {
                        try {
                            builder.port(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid port number: {}", value);
                        }
                    }
                    case "independentDeployment" -> builder.independentDeployment(Boolean.parseBoolean(value));
                }
            }
        }

        List<String> dependencies = findDependencies(classDecl);
        builder.dependencies(dependencies);

        FractalModule module = builder.build();

        if (!dependencies.isEmpty()) {
            log.info("Detected dependencies for {}: {}", module.getServiceName(), dependencies);
        }

        return module;
    }

    /**
     * Finds cross-module dependencies by inspecting injected fields and constructor parameters.
     * Types ending with "Client" or "Service" are treated as potential cross-module calls.
     */
    private List<String> findDependencies(ClassOrInterfaceDeclaration classDecl) {
        Set<String> dependencies = new HashSet<>();

        classDecl.findAll(FieldDeclaration.class).forEach(field -> {
            String fieldType = field.getCommonType().asString();
            if (fieldType.endsWith("Client") || fieldType.endsWith("Service")) {
                dependencies.add(fieldType);
                log.debug("Found dependency: {} in field declaration", fieldType);
            }
        });

        classDecl.getConstructors().forEach(constructor ->
                constructor.getParameters().forEach(param -> {
                    String paramType = param.getType().asString();
                    if (paramType.endsWith("Client") || paramType.endsWith("Service")) {
                        dependencies.add(paramType);
                        log.debug("Found dependency: {} in constructor parameter", paramType);
                    }
                })
        );

        return new ArrayList<>(dependencies);
    }
}
