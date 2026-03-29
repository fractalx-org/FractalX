package org.fractalx.core.validation.rules;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Warns when a {@code @DecomposableModule} class uses {@code @AllArgsConstructor}
 * (or has no constructor at all) instead of {@code @RequiredArgsConstructor}.
 *
 * <h3>Why this matters</h3>
 * JavaParser reads the <em>source</em> AST — Lombok generates constructors at
 * <em>compile</em> time. So {@code @AllArgsConstructor} produces no constructor
 * node in the AST. {@code ModuleAnalyzer.findDependencies()} falls back to the
 * field-type suffix heuristic ({@code *Service}, {@code *Client}) which silently
 * misses types like {@code BillingEngine} or {@code NotificationGateway}.
 *
 * <h3>What to do</h3>
 * Switch to {@code @RequiredArgsConstructor} and make every injected service
 * field {@code private final}. Alternatively, declare the dependencies
 * explicitly in {@code @DecomposableModule(dependencies = {...})}.
 */
public class LombokConstructorRule implements ValidationRule {

    private static final Logger log = LoggerFactory.getLogger(LombokConstructorRule.class);

    @Override
    public String ruleId() { return "LOMBOK_ALL_ARGS"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) throws IOException {
        List<ValidationIssue> issues = new ArrayList<>();
        JavaParser parser = new JavaParser();

        for (FractalModule module : ctx.modules()) {
            Path sourceFile = resolveSourceFile(ctx.sourceRoot(), module);
            if (sourceFile == null || !Files.exists(sourceFile)) {
                log.debug("LombokConstructorRule: source file not found for {}", module.getClassName());
                continue;
            }

            Optional<CompilationUnit> cu = parser.parse(sourceFile).getResult();
            if (cu.isEmpty()) continue;

            Optional<ClassOrInterfaceDeclaration> classDecl = cu.get()
                    .findFirst(ClassOrInterfaceDeclaration.class,
                            c -> c.getNameAsString().equals(simpleClassName(module.getClassName())));
            if (classDecl.isEmpty()) continue;

            ClassOrInterfaceDeclaration cls = classDecl.get();

            boolean hasAllArgs      = cls.getAnnotationByName("AllArgsConstructor").isPresent();
            boolean hasExplicitDeps = !module.getDependencies().isEmpty();

            if (hasAllArgs && !hasExplicitDeps) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING, ruleId(), module.getServiceName(),
                        module.getClassName() + " uses @AllArgsConstructor — "
                        + "ModuleAnalyzer cannot reliably detect cross-module dependencies "
                        + "from Lombok-generated constructors",
                        "Switch to @RequiredArgsConstructor and mark each injected service "
                        + "field as 'private final', OR declare dependencies explicitly: "
                        + "@DecomposableModule(dependencies = {PaymentService.class, ...})"));
            }
        }
        return issues;
    }

    private Path resolveSourceFile(Path sourceRoot, FractalModule module) {
        String className = module.getClassName();
        if (className == null || className.isBlank()) return null;
        // Convert fully-qualified class name to file path
        String relativePath = className.replace('.', '/') + ".java";
        return sourceRoot.resolve(relativePath);
    }

    private String simpleClassName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }
}
