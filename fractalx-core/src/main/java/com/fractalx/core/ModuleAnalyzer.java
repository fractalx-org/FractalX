package com.fractalx.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
 * Analyzes source code to identify FractalX decomposable modules
 */
public class ModuleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ModuleAnalyzer.class);
    private final JavaParser javaParser;

    public ModuleAnalyzer() {
        this.javaParser = new JavaParser();
    }

    /**
     * Analyzes a source directory and identifies decomposable modules
     */
    public List<FractalModule> analyzeProject(Path sourceRoot) throws IOException {
        List<FractalModule> modules = new ArrayList<>();

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        analyzeFile(path, modules);
                    } catch (IOException e) {
                        log.error("Failed to analyze file: " + path, e);
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
                FractalModule module = extractModuleDescriptor(classDecl, annotation.get());
                modules.add(module);
                log.info("Found decomposable module: " + module.getServiceName());
            }
        });
    }

    private FractalModule extractModuleDescriptor(
            ClassOrInterfaceDeclaration classDecl,
            AnnotationExpr annotation) {

        FractalModule descriptor = new FractalModule();
        descriptor.setClassName(classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString()));

        // Extract package name
        classDecl.findCompilationUnit().ifPresent(cu -> {
            cu.getPackageDeclaration().ifPresent(pd -> {
                descriptor.setPackageName(pd.getNameAsString());
            });
        });

        // Extract annotation properties
        if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotation = annotation.asNormalAnnotationExpr();
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String name = pair.getNameAsString();
                String value = pair.getValue().toString().replace("\"", "");

                switch (name) {
                    case "serviceName":
                        descriptor.setServiceName(value);
                        break;
                    case "port":
                        try {
                            descriptor.setPort(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid port number: " + value);
                        }
                        break;
                    case "independentDeployment":
                        descriptor.setIndependentDeployment(Boolean.parseBoolean(value));
                        break;
                }
            }
        }

        // Analyze method calls to detect cross-module dependencies
        List<String> dependencies = findCrossModuleDependencies(classDecl);
        descriptor.setDependencies(dependencies);

        return descriptor;
    }

    private List<String> findCrossModuleDependencies(ClassOrInterfaceDeclaration classDecl) {
        Set<String> dependencies = new HashSet<>();

        classDecl.findAll(MethodDeclaration.class).forEach(method -> {
            method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)
                    .forEach(methodCall -> {
                        // Detect calls to other services
                        methodCall.getScope().ifPresent(scope -> {
                            try {
                                String scopeType = scope.calculateResolvedType().describe();
                                if (scopeType.contains("Client") || scopeType.contains("Service")) {
                                    dependencies.add(scopeType);
                                }
                            } catch (Exception e) {
                                // Type resolution might fail for uncompiled code
                                log.debug("Could not resolve type for: " + scope);
                            }
                        });
                    });
        });

        return new ArrayList<>(dependencies);
    }
}