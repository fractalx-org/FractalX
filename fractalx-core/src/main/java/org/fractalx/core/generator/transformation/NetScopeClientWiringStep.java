package org.fractalx.core.generator.transformation;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.generator.service.NetScopeClientGenerator;
import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Rewires cross-module service references in copied source files so they point to
 * the generated {@code *Client} interfaces instead of the original service classes.
 *
 * <p>After {@link NetScopeClientGenerator} generates a {@code PaymentServiceClient}
 * interface for an {@code order-service}, this step walks every {@code .java} file
 * in the generated service and performs the following rewrites for each dependency
 * type (e.g., {@code PaymentService}):
 *
 * <ol>
 *   <li>Field declaration type: {@code PaymentService → PaymentServiceClient}</li>
 *   <li>Field variable name: rewired using the <em>actual declared field name</em>,
 *       not an assumed {@code decapitalize(TypeName)} convention</li>
 *   <li>Constructor / method parameter types and names: same rename</li>
 *   <li>All {@code NameExpr} references ({@code paymentService.foo()} call sites)</li>
 *   <li>All {@code FieldAccessExpr} references ({@code this.paymentService})</li>
 *   <li>Import of the original remote class removed using its fully-qualified name
 *       (resolved from the module list) to avoid removing unrelated same-named classes</li>
 * </ol>
 *
 * <p>No import is added for the client interface because it is generated in the
 * same package as the consuming service class.
 */
public class NetScopeClientWiringStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(NetScopeClientWiringStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        List<String> deps = module.getDependencies();

        if (deps.isEmpty()) {
            log.debug("No dependencies for {} – skipping NetScope client wiring", module.getServiceName());
            return;
        }

        Files.walk(context.getSrcMainJava())
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(javaFile -> {
                    try {
                        wireClientsInFile(javaFile, deps, context);
                    } catch (IOException e) {
                        log.error("Failed to wire NetScope clients in: {}", javaFile.getFileName(), e);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Per-file rewriting
    // -------------------------------------------------------------------------

    private void wireClientsInFile(Path javaFile, List<String> deps, GenerationContext context)
            throws IOException {
        CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
        if (cu == null) return;

        boolean modified = false;

        for (String beanType : deps) {
            String clientType = beanType + "Client";

            // Guard: only wire if NetScopeClientGenerator would have resolved the target module.
            // Both steps use the same two-pass (exact → normalized) lookup so they stay consistent:
            // if the generator cannot find the module and skips writing the interface, the wiring
            // step also skips — preventing a compile error from a reference to a non-existent type.
            if (!isTargetModuleResolvable(beanType, context)) {
                log.debug("Cannot resolve target module for dep '{}' — skipping client wiring", beanType);
                continue;
            }

            if (!referencesType(cu, beanType)) {
                continue; // no field of this type in the file — skip
            }

            // Determine the actual declared field name (AST-based, not convention-assumed).
            // Fall back to decapitalize(beanType) only if no field is found.
            String oldFieldName = findFieldNameForType(cu, beanType)
                    .orElseGet(() -> {
                        String fallback = decapitalize(beanType);
                        log.debug("No field of type {} found in {}; using name heuristic '{}'",
                                beanType, javaFile.getFileName(), fallback);
                        return fallback;
                    });
            String newFieldName = oldFieldName + "Client";

            // 1. Field declaration: type + variable name
            for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                for (VariableDeclarator var : field.getVariables()) {
                    if (var.getTypeAsString().equals(beanType)) {
                        var.setType(new ClassOrInterfaceType(null, clientType));
                        if (var.getNameAsString().equals(oldFieldName)) {
                            var.setName(newFieldName);
                        }
                        modified = true;
                    }
                }
            }

            // 2. Constructor / method parameters: type + name
            for (Parameter param : cu.findAll(Parameter.class)) {
                if (param.getTypeAsString().equals(beanType)) {
                    param.setType(new ClassOrInterfaceType(null, clientType));
                    if (param.getNameAsString().equals(oldFieldName)) {
                        param.setName(newFieldName);
                    }
                    modified = true;
                }
            }

            // 3. NameExpr references (method call receivers, RHS of assignments, etc.)
            for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
                if (nameExpr.getNameAsString().equals(oldFieldName)) {
                    nameExpr.setName(newFieldName);
                    modified = true;
                }
            }

            // 4. FieldAccessExpr references (this.paymentService → this.paymentServiceClient)
            for (FieldAccessExpr fae : cu.findAll(FieldAccessExpr.class)) {
                if (fae.getNameAsString().equals(oldFieldName)) {
                    fae.setName(newFieldName);
                    modified = true;
                }
            }

            // 5. Remove import of the original remote class (no longer present in this service).
            //    Resolve the FQN from the module list to avoid removing unrelated classes
            //    that happen to share the same simple name.
            removeImport(cu, beanType, context);
        }

        if (modified) {
            Files.writeString(javaFile, cu.toString());
            log.info("Wired NetScope client references in: {}", javaFile.getFileName());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the target module for {@code beanType} can be resolved
     * using the same two-pass (exact → normalized) lookup that
     * {@link org.fractalx.core.generator.service.NetScopeClientGenerator} uses.
     *
     * <p>Both steps must agree on resolvability: if the generator cannot find the module
     * and skips writing the client interface, this step must also skip the rename — otherwise
     * the generated source references a type that does not exist, causing a compile error.
     */
    private boolean isTargetModuleResolvable(String beanType, GenerationContext context) {
        String derivedName      = NetScopeClientGenerator.beanTypeToServiceName(beanType);
        String normalizedDerived = NetScopeClientGenerator.normalizeModuleName(derivedName);

        return context.getAllModules().stream()
                .filter(m -> !m.equals(context.getModule()))
                .anyMatch(m -> derivedName.equals(m.getServiceName())
                        || normalizedDerived.equals(
                                NetScopeClientGenerator.normalizeModuleName(m.getServiceName())));
    }

    /**
     * AST-based type reference check: returns true only if the compilation unit
     * declares a field whose type is exactly {@code typeName}. This avoids false
     * positives from string literals, comments, and partial name matches.
     */
    private boolean referencesType(CompilationUnit cu, String typeName) {
        return cu.findAll(FieldDeclaration.class).stream()
                .anyMatch(f -> f.getCommonType().asString().equals(typeName));
    }

    /**
     * Finds the actual declared name of the first field with the given type.
     * Returns empty if no such field exists in the compilation unit.
     */
    private Optional<String> findFieldNameForType(CompilationUnit cu, String typeName) {
        return cu.findAll(FieldDeclaration.class).stream()
                .filter(f -> f.getCommonType().asString().equals(typeName))
                .flatMap(f -> f.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .findFirst();
    }

    /**
     * Removes the import for {@code beanType} using its fully-qualified name resolved from
     * the module list. Falls back to simple-name suffix match (with a warning) when the FQN
     * cannot be determined, to avoid silently doing nothing.
     */
    private void removeImport(CompilationUnit cu, String beanType, GenerationContext context) {
        // Resolve FQN: find the module that owns this bean type (its packageName + beanType)
        String fqn = context.getAllModules().stream()
                .filter(m -> !m.equals(context.getModule()))   // not the current module
                .filter(m -> m.getPackageName() != null)
                .map(m -> m.getPackageName() + "." + beanType)
                .filter(candidate -> cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals(candidate)))
                .findFirst()
                .orElse(null);

        if (fqn != null) {
            cu.getImports().removeIf(imp -> imp.getNameAsString().equals(fqn));
        } else {
            // FQN not resolved — fall back to suffix match but log a warning
            boolean removed = cu.getImports().removeIf(imp ->
                    !imp.isAsterisk()
                    && !imp.isStatic()
                    && imp.getNameAsString().endsWith("." + beanType));
            if (removed) {
                log.debug("Removed import for {} using suffix fallback (FQN not resolved)", beanType);
            }
        }
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
