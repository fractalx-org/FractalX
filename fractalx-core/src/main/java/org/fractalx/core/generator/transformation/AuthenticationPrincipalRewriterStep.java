package org.fractalx.core.generator.transformation;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rewrites {@code @AuthenticationPrincipal} parameter types in copied controller source code
 * to use {@link org.fractalx.runtime.GatewayPrincipal}.
 *
 * <p>In the monolith, controllers typically use:
 * <pre>
 * {@code @GetMapping("/orders")
 * public List<Order> list(@AuthenticationPrincipal User user) { ... }}
 * </pre>
 *
 * After decomposition, the monolith's {@code User} entity no longer exists in every service —
 * it belongs exclusively to the auth-service. This step rewrites the parameter type to
 * {@code GatewayPrincipal}, which is reconstructed from the gateway's Internal Call Token:
 * <pre>
 * {@code @GetMapping("/orders")
 * public List<Order> list(@AuthenticationPrincipal GatewayPrincipal user) { ... }}
 * </pre>
 *
 * <p>This step runs after {@link ServiceSecurityStep} (which ensures security is configured)
 * and after code has been copied into the service directory.
 *
 * <p>When security is not enabled, this step is a no-op.
 */
public class AuthenticationPrincipalRewriterStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationPrincipalRewriterStep.class);

    private static final String GATEWAY_PRINCIPAL_FQN = "org.fractalx.runtime.GatewayPrincipal";
    private static final String GATEWAY_PRINCIPAL_SIMPLE = "GatewayPrincipal";
    private static final String AUTH_PRINCIPAL_ANNOTATION = "AuthenticationPrincipal";

    @Override
    public void generate(GenerationContext context) throws IOException {
        if (!context.isSecurityEnabled()) {
            return;
        }

        Path srcDir = context.getSrcMainJava();
        if (!Files.isDirectory(srcDir)) return;

        Set<String> rewrittenFiles = new LinkedHashSet<>();

        try (Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            if (rewriteFile(file)) {
                                rewrittenFiles.add(file.getFileName().toString());
                            }
                        } catch (Exception e) {
                            log.debug("Could not process {} for @AuthenticationPrincipal rewrite: {}",
                                    file, e.getMessage());
                        }
                    });
        }

        if (!rewrittenFiles.isEmpty()) {
            log.info("  Rewrote @AuthenticationPrincipal → GatewayPrincipal in: {}", rewrittenFiles);
        }
    }

    /**
     * Parses a single Java file and rewrites any {@code @AuthenticationPrincipal} parameter
     * type to {@code GatewayPrincipal}. Returns {@code true} if the file was modified.
     */
    private boolean rewriteFile(Path file) throws IOException {
        String source = Files.readString(file);
        if (!source.contains(AUTH_PRINCIPAL_ANNOTATION)) {
            return false;
        }

        CompilationUnit cu = StaticJavaParser.parse(source);
        boolean modified = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            for (Parameter param : method.getParameters()) {
                if (param.getAnnotationByName(AUTH_PRINCIPAL_ANNOTATION).isEmpty()) {
                    continue;
                }
                String currentType = param.getTypeAsString();
                if (GATEWAY_PRINCIPAL_SIMPLE.equals(currentType)) {
                    continue; // already rewritten
                }

                log.debug("Rewriting @AuthenticationPrincipal {} → {} in {}#{}",
                        currentType, GATEWAY_PRINCIPAL_SIMPLE, file.getFileName(), method.getNameAsString());
                param.setType(GATEWAY_PRINCIPAL_SIMPLE);
                modified = true;
            }
        }

        if (modified) {
            // Add GatewayPrincipal import if not present
            boolean hasImport = cu.getImports().stream()
                    .anyMatch(i -> i.getNameAsString().equals(GATEWAY_PRINCIPAL_FQN));
            if (!hasImport) {
                cu.addImport(new ImportDeclaration(GATEWAY_PRINCIPAL_FQN, false, false));
            }

            // Remove old UserDetails/User imports that are no longer needed
            // (only remove if they were the @AuthenticationPrincipal type)
            cu.getImports().removeIf(i -> {
                String name = i.getNameAsString();
                // Don't remove standard Spring Security imports
                if (name.startsWith("org.springframework.security.")) return false;
                // Remove imports of types that are no longer referenced in the file
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                return !cu.toString().contains(simpleName + " ")
                        && !cu.toString().contains(simpleName + ".")
                        && !cu.toString().contains(simpleName + "<")
                        && !cu.toString().contains(simpleName + ",")
                        && !cu.toString().contains(simpleName + ")");
            });

            Files.writeString(file, cu.toString());
        }
        return modified;
    }
}
