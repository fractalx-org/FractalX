package org.fractalx.core.generator.transformation;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Rewires cross-module service references in copied source files so they point to
 * the generated {@code *Client} interfaces instead of the original service classes.
 *
 * <p>After {@link NetScopeClientWiringStep} generates a {@code PaymentServiceClient}
 * interface for an {@code order-service}, this step walks every {@code .java} file
 * in the generated service and performs the following rewrites for each dependency
 * type (e.g., {@code PaymentService}):
 *
 * <ol>
 *   <li>Field declaration type: {@code PaymentService → PaymentServiceClient}</li>
 *   <li>Field variable name: {@code paymentService → paymentServiceClient}</li>
 *   <li>Constructor / method parameter types and names: same rename</li>
 *   <li>All {@code NameExpr} references ({@code paymentService.foo()} call sites):
 *       renamed to {@code paymentServiceClient.foo()}</li>
 *   <li>All {@code FieldAccessExpr} references ({@code this.paymentService}):
 *       renamed to {@code this.paymentServiceClient}</li>
 *   <li>Import of the original remote class removed (it no longer exists in this service)</li>
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
                        wireClientsInFile(javaFile, deps);
                    } catch (IOException e) {
                        log.error("Failed to wire NetScope clients in: {}", javaFile.getFileName(), e);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Per-file rewriting
    // -------------------------------------------------------------------------

    private void wireClientsInFile(Path javaFile, List<String> deps) throws IOException {
        CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
        if (cu == null) return;

        boolean modified = false;

        for (String beanType : deps) {
            String clientType   = beanType + "Client";
            String oldFieldName = decapitalize(beanType);
            String newFieldName = decapitalize(clientType);

            if (!referencesType(cu, beanType)) {
                continue; // nothing to rewrite in this file
            }

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
            //    The generated *Client interface lives in the same package — no import needed.
            cu.getImports().removeIf(imp ->
                    !imp.isAsterisk()
                    && !imp.isStatic()
                    && imp.getNameAsString().endsWith("." + beanType));
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
     * Quick check: does this compilation unit reference the given type name anywhere?
     * Avoids unnecessary AST traversal for files that clearly don't use the type.
     */
    private boolean referencesType(CompilationUnit cu, String typeName) {
        return cu.toString().contains(typeName);
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
