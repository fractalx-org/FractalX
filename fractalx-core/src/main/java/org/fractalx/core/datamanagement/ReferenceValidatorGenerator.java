package org.fractalx.core.datamanagement;

import org.fractalx.core.generator.service.NetScopeClientGenerator;
import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generates a {@code ReferenceValidator} Spring bean that enforces cross-service
 * referential integrity using NetScope client calls.
 *
 * <p>After {@link RelationshipDecoupler} converts JPA relationships to String ID
 * fields (e.g., {@code Customer customer} → {@code String customerId}), there is no
 * database-level foreign key enforcing that the ID references a real record in the
 * remote service. This generator bridges that gap by producing a validator that
 * calls the remote service's NetScope endpoint to verify existence before a save.
 *
 * <p>For each dependency declared on the module, the generator:
 * <ol>
 *   <li>Detects {@code String *Id} fields that were produced by {@link RelationshipDecoupler}.</li>
 *   <li>Generates a {@code validate<Type>Exists(String id)} method that calls
 *       {@code <TypeClient>.exists(id)} via NetScope.</li>
 *   <li>Generates a {@code <Type>Client} interface stub with an {@code exists} method
 *       if it does not already exist.</li>
 * </ol>
 *
 * <p>Developers add {@code referenceValidator.validateCustomerExists(customerId)} calls
 * inside their service methods before persisting.
 */
public class ReferenceValidatorGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReferenceValidatorGenerator.class);

    private final JavaParser javaParser = new JavaParser();

    public void generateReferenceValidator(FractalModule module, Path serviceRoot) throws IOException {
        List<String> deps = module.getDependencies();
        if (deps.isEmpty()) {
            log.debug("No cross-module dependencies for {} — ReferenceValidator not generated",
                    module.getServiceName());
            return;
        }

        // Find which remote entity types remain as String ID fields after RelationshipDecoupler
        Set<String> remoteIdTypes = detectDecoupledIdTypes(serviceRoot.resolve("src/main/java"));
        if (remoteIdTypes.isEmpty()) {
            log.debug("No decoupled ID fields detected for {} — ReferenceValidator not generated",
                    module.getServiceName());
            return;
        }

        String basePackage      = "org.fractalx.generated." + module.getServiceName().replace("-", "");
        String validationPackage = basePackage + ".validation";

        Path validationPath = createPackagePath(serviceRoot.resolve("src/main/java"), validationPackage);

        // Generate ReferenceValidator bean
        Files.writeString(
                validationPath.resolve("ReferenceValidator.java"),
                buildReferenceValidator(validationPackage, basePackage, module, remoteIdTypes)
        );

        // Generate exists() stub for each remote type's NetScope client if not already present
        for (String remoteType : remoteIdTypes) {
            String clientName     = remoteType + "ExistsClient";
            String targetService  = NetScopeClientGenerator.beanTypeToServiceName(remoteType);
            Path   clientFile     = validationPath.resolve(clientName + ".java");

            if (!Files.exists(clientFile)) {
                Files.writeString(clientFile, buildExistsClient(validationPackage, clientName,
                        remoteType, targetService, module));
            }
        }

        log.info("Generated ReferenceValidator for {} — covers types: {}", module.getServiceName(), remoteIdTypes);
    }

    // -------------------------------------------------------------------------
    // Detection: find remote types that were decoupled to String IDs
    // -------------------------------------------------------------------------

    /**
     * Scans entity classes for fields that look like decoupled ID fields:
     * {@code String *Id} (where the suffix before "Id" is a known remote type name).
     * These are created by {@link RelationshipDecoupler}.
     */
    private Set<String> detectDecoupledIdTypes(Path srcMainJava) {
        Set<String> types = new LinkedHashSet<>();
        if (!Files.exists(srcMainJava)) return types;

        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                    if (cu == null) return;

                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (!hasAnnotation(cls, "Entity")) continue;

                        for (FieldDeclaration field : cls.getFields()) {
                            String typeName = field.getElementType().asString();
                            if (!"String".equals(typeName)) continue;

                            field.getVariables().forEach(v -> {
                                String name = v.getNameAsString();
                                // Detect pattern: someEntityId → "SomeEntity"
                                if (name.endsWith("Id") && name.length() > 2) {
                                    String base = name.substring(0, name.length() - 2);
                                    if (!base.isEmpty()) {
                                        String typeName2 = Character.toUpperCase(base.charAt(0)) + base.substring(1);
                                        types.add(typeName2);
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan for decoupled ID fields: {}", e.getMessage());
        }
        return types;
    }

    // -------------------------------------------------------------------------
    // Code generation
    // -------------------------------------------------------------------------

    private String buildReferenceValidator(String validationPackage,
                                           String basePackage,
                                           FractalModule module,
                                           Set<String> remoteTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(validationPackage).append(";\n\n");

        for (String type : remoteTypes) {
            sb.append("import ").append(validationPackage).append(".").append(type).append("ExistsClient;\n");
        }
        sb.append("""
                import jakarta.persistence.EntityNotFoundException;
                import org.springframework.stereotype.Component;

                """);

        sb.append("/**\n");
        sb.append(" * Validates cross-service referential integrity using NetScope client calls.\n");
        sb.append(" *\n");
        sb.append(" * <p>Call these methods before persisting entities with remote ID references\n");
        sb.append(" * to ensure the referenced record exists in the remote service.\n");
        sb.append(" *\n");
        sb.append(" * <pre>\n");
        sb.append(" * // Example usage in a @Transactional service method:\n");
        sb.append(" * referenceValidator.validate").append(remoteTypes.iterator().next())
          .append("Exists(").append(Character.toLowerCase(remoteTypes.iterator().next().charAt(0)))
          .append(remoteTypes.iterator().next().substring(1)).append("Id);\n");
        sb.append(" * </pre>\n");
        sb.append(" *\n * Auto-generated by FractalX — service: ").append(module.getServiceName()).append(".\n */\n");
        sb.append("@Component\n");
        sb.append("public class ReferenceValidator {\n\n");

        // Fields
        for (String type : remoteTypes) {
            String fieldName = Character.toLowerCase(type.charAt(0)) + type.substring(1) + "ExistsClient";
            sb.append("    private final ").append(type).append("ExistsClient ").append(fieldName).append(";\n");
        }
        sb.append("\n");

        // Constructor
        sb.append("    public ReferenceValidator(");
        List<String> ctorParams = new ArrayList<>();
        for (String type : remoteTypes) {
            String clientType = type + "ExistsClient";
            String fieldName  = Character.toLowerCase(type.charAt(0)) + type.substring(1) + "ExistsClient";
            ctorParams.add(clientType + " " + fieldName);
        }
        sb.append(String.join(", ", ctorParams)).append(") {\n");
        for (String type : remoteTypes) {
            String fieldName = Character.toLowerCase(type.charAt(0)) + type.substring(1) + "ExistsClient";
            sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
        }
        sb.append("    }\n\n");

        // Validate methods
        for (String type : remoteTypes) {
            String clientField = Character.toLowerCase(type.charAt(0)) + type.substring(1) + "ExistsClient";
            String param       = Character.toLowerCase(type.charAt(0)) + type.substring(1) + "Id";
            sb.append("    /**\n");
            sb.append("     * Verifies that a ").append(type).append(" with the given ID exists in the remote service.\n");
            sb.append("     * @throws EntityNotFoundException if the ID cannot be resolved\n");
            sb.append("     */\n");
            sb.append("    public void validate").append(type).append("Exists(String ").append(param).append(") {\n");
            sb.append("        if (").append(param).append(" == null || ").append(param).append(".isBlank()) {\n");
            sb.append("            throw new EntityNotFoundException(\"").append(type)
              .append(" ID must not be null or blank\");\n");
            sb.append("        }\n");
            sb.append("        boolean exists = ").append(clientField).append(".exists(").append(param).append(");\n");
            sb.append("        if (!exists) {\n");
            sb.append("            throw new EntityNotFoundException(\"").append(type)
              .append(" not found: \" + ").append(param).append(");\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildExistsClient(String pkg, String clientName,
                                     String remoteType, String targetService,
                                     FractalModule module) {
        return """
                package %s;

                import org.fractalx.netscope.client.annotation.NetScopeClient;

                /**
                 * NetScope existence-check client for %s.
                 * Used by ReferenceValidator to enforce cross-service referential integrity.
                 *
                 * IMPORTANT: The remote service (%s) must expose an {@code exists(String id)}
                 * method annotated with {@code @NetworkPublic} on its %s bean.
                 * Add this to the remote service if not already present:
                 *
                 * <pre>
                 * {@code @NetworkPublic}
                 * public boolean exists(String id) {
                 *     return repository.existsById(id);
                 * }
                 * </pre>
                 *
                 * Auto-generated by FractalX — used by service: %s.
                 */
                @NetScopeClient(server = "%s", beanName = "%s")
                public interface %s {

                    /**
                     * Returns true if an entity with this ID exists in %s.
                     */
                    boolean exists(String id);
                }
                """.formatted(
                pkg,
                remoteType,
                targetService, remoteType,
                module.getServiceName(),
                targetService, remoteType,
                clientName,
                remoteType
        );
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private boolean hasAnnotation(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(name));
    }

    private Path createPackagePath(Path root, String packageName) throws IOException {
        Path path = root;
        for (String part : packageName.split("\\.")) {
            path = path.resolve(part);
        }
        Files.createDirectories(path);
        return path;
    }
}
