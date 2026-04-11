package org.fractalx.core.generator.transformation;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

    private static final String GATEWAY_PRINCIPAL_FQN    = "org.fractalx.runtime.GatewayPrincipal";
    private static final String GATEWAY_PRINCIPAL_SIMPLE  = "GatewayPrincipal";
    private static final String AUTH_PRINCIPAL_ANNOTATION = "AuthenticationPrincipal";

    /** Methods that exist on {@link org.fractalx.runtime.GatewayPrincipal} — calls to these are kept as-is. */
    private static final Set<String> GATEWAY_PRINCIPAL_METHODS = Set.of(
            "getId", "getUsername", "getEmail", "getAuthorities", "getAttributes",
            "getAttribute", "hasRole", "getPassword",
            "isAccountNonExpired", "isAccountNonLocked", "isCredentialsNonExpired", "isEnabled"
    );

    @Override
    public void generate(GenerationContext context) throws IOException {
        if (!context.isSecurityEnabled()) {
            return;
        }

        Path srcDir = context.getSrcMainJava();
        if (!Files.isDirectory(srcDir)) return;

        Set<String> rewrittenFiles = new LinkedHashSet<>();
        // Tracks simple type names that were replaced (e.g., "User") so their
        // orphaned source files can be deleted after all rewrites are done.
        Set<String> replacedTypes = new LinkedHashSet<>();

        // Pre-scan: collect getter→returnType for every .java file in the service
        // so we can generate properly-typed getAttribute() casts when needed.
        Map<String, Map<String, String>> typeGetterReturnTypes =
                collectGetterReturnTypes(srcDir);

        try (Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            Set<String> typesReplaced = rewriteFile(file, typeGetterReturnTypes);
                            if (!typesReplaced.isEmpty()) {
                                rewrittenFiles.add(file.getFileName().toString());
                                replacedTypes.addAll(typesReplaced);
                            }
                        } catch (Exception e) {
                            log.debug("Could not process {} for @AuthenticationPrincipal rewrite: {}",
                                    file, e.getMessage());
                        }
                    });
        }

        if (!rewrittenFiles.isEmpty()) {
            log.info("  Rewrote @AuthenticationPrincipal → GatewayPrincipal in: {}", rewrittenFiles);
            deleteOrphanedAuthTypes(srcDir, replacedTypes);
        }
    }

    /**
     * Parses a single Java file and rewrites any {@code @AuthenticationPrincipal} parameter
     * type to {@code GatewayPrincipal}, and rewrites getter calls on that parameter that do
     * not exist on {@code GatewayPrincipal} to {@code getAttribute("fieldName")} with an
     * appropriate cast. Returns the set of simple type names that were replaced (empty if
     * the file was not modified).
     */
    private Set<String> rewriteFile(Path file,
                                    Map<String, Map<String, String>> typeGetterReturnTypes)
            throws IOException {
        String source = Files.readString(file);
        if (!source.contains(AUTH_PRINCIPAL_ANNOTATION)) {
            return Set.of();
        }

        CompilationUnit cu = StaticJavaParser.parse(source);
        Set<String> replacedTypes = new HashSet<>();
        // Maps principal parameter name → replaced type simple name (e.g. "user" → "User")
        Map<String, String> paramNameToOldType = new HashMap<>();

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
                paramNameToOldType.put(param.getNameAsString(), currentType);
                param.setType(GATEWAY_PRINCIPAL_SIMPLE);
                replacedTypes.add(currentType);
            }
        }

        if (!replacedTypes.isEmpty()) {
            // Rewrite getter calls on the replaced principal parameter that don't exist on
            // GatewayPrincipal (e.g. user.getCustomerId()) to getAttribute("customerId").
            rewriteGetterCalls(cu, paramNameToOldType, typeGetterReturnTypes);

            // Add GatewayPrincipal import if not present
            boolean hasImport = cu.getImports().stream()
                    .anyMatch(i -> i.getNameAsString().equals(GATEWAY_PRINCIPAL_FQN));
            if (!hasImport) {
                cu.addImport(new ImportDeclaration(GATEWAY_PRINCIPAL_FQN, false, false));
            }

            // Remove imports only for the types we actually replaced — not all "unused" imports.
            // A broad unused-import check would incorrectly remove annotation imports such as
            // @RequiredArgsConstructor whose simple name never appears as "Name " or "Name.".
            cu.getImports().removeIf(i -> {
                String name = i.getNameAsString();
                if (name.startsWith("org.springframework.security.")) return false;
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                return replacedTypes.contains(simpleName);
            });

            Files.writeString(file, cu.toString());
        }
        return replacedTypes;
    }

    /**
     * Rewrites getter calls on the replaced principal parameters that do not exist on
     * {@link org.fractalx.runtime.GatewayPrincipal}.
     *
     * <p>For example: {@code user.getCustomerId()} → {@code (Long) user.getAttribute("customerId")}
     *
     * <p>The return type for the cast is looked up from {@code typeGetterReturnTypes}. If the
     * return type is a primitive wrapper for a number ({@code Long}, {@code Integer}, etc.)
     * the cast uses {@code Number} to avoid ClassCastException when JSON deserialises integers
     * as {@code Integer}: e.g. {@code ((Number) user.getAttribute("customerId")).longValue()}.
     */
    private void rewriteGetterCalls(CompilationUnit cu,
                                    Map<String, String> paramNameToOldType,
                                    Map<String, Map<String, String>> typeGetterReturnTypes) {
        if (paramNameToOldType.isEmpty()) return;

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getScope().isEmpty()) continue;
            if (!(call.getScope().get() instanceof NameExpr scopeName)) continue;

            String paramName = scopeName.getNameAsString();
            String oldType   = paramNameToOldType.get(paramName);
            if (oldType == null) continue;

            String methodName = call.getNameAsString();
            if (GATEWAY_PRINCIPAL_METHODS.contains(methodName)) continue;
            if (!call.getArguments().isEmpty()) continue; // only zero-arg getters

            // Derive the attribute key: strip "get" prefix and lowercase first letter
            String attrKey = deriveAttributeKey(methodName);
            if (attrKey == null) continue;

            // Look up the declared return type to generate a typed cast
            Map<String, String> getters = typeGetterReturnTypes.getOrDefault(oldType, Map.of());
            String returnType = getters.get(methodName);

            MethodCallExpr getAttrCall = new MethodCallExpr(
                    new NameExpr(paramName), "getAttribute",
                    new NodeList<>(new StringLiteralExpr(attrKey)));

            if (returnType != null && isNumberType(returnType)) {
                // Claims are stored as Strings in the JWT (see AuthServiceGenerator / JwtBearerFilter).
                // Use String.valueOf() before parsing so this handles both String and Number attributes.
                // Wrap in a null check so missing claims return null rather than throwing NPE/NFE:
                //   principal.getAttribute("customerId") != null
                //       ? Long.parseLong(String.valueOf(principal.getAttribute("customerId")))
                //       : null
                MethodCallExpr getAttrForCheck = new MethodCallExpr(
                        new NameExpr(paramName), "getAttribute",
                        new NodeList<>(new StringLiteralExpr(attrKey)));
                MethodCallExpr stringValueOf = new MethodCallExpr(
                        new NameExpr("String"), "valueOf", new NodeList<>(getAttrCall));
                MethodCallExpr parsed = new MethodCallExpr(
                        new NameExpr(toParseClass(returnType)),
                        toParseMethod(returnType),
                        new NodeList<>(stringValueOf));
                ConditionalExpr nullSafe = new ConditionalExpr(
                        new EnclosedExpr(new BinaryExpr(
                                getAttrForCheck, new NullLiteralExpr(),
                                BinaryExpr.Operator.NOT_EQUALS)),
                        parsed,
                        new NullLiteralExpr());
                call.replace(nullSafe);
            } else if (returnType != null) {
                call.replace(new EnclosedExpr(
                        new CastExpr(new ClassOrInterfaceType(null, returnType), getAttrCall)));
            } else {
                call.replace(getAttrCall);
            }

            log.debug("Rewrote {}.{}() → getAttribute(\"{}\") in file",
                    paramName, methodName, attrKey);
        }
    }

    /** Converts a getter name to its attribute key, e.g. {@code getCustomerId} → {@code customerId}. */
    private static String deriveAttributeKey(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String bare = methodName.substring(3);
            return Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            String bare = methodName.substring(2);
            return Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
        }
        return null;
    }

    private static boolean isNumberType(String type) {
        return switch (type) {
            case "Long", "long", "Integer", "int", "Short", "short",
                 "Byte", "byte", "Double", "double", "Float", "float" -> true;
            default -> false;
        };
    }

    private static String toParseClass(String type) {
        return switch (type) {
            case "Integer", "int"   -> "Integer";
            case "Double", "double" -> "Double";
            case "Float", "float"   -> "Float";
            default                 -> "Long";
        };
    }

    private static String toParseMethod(String type) {
        return switch (type) {
            case "Integer", "int"   -> "parseInt";
            case "Double", "double" -> "parseDouble";
            case "Float", "float"   -> "parseFloat";
            default                 -> "parseLong";
        };
    }

    /**
     * Scans all {@code .java} files in {@code srcDir} and builds a map of
     * {@code simpleClassName → (getterMethodName → returnType)} for use when
     * rewriting getter calls on replaced principal types.
     */
    private Map<String, Map<String, String>> collectGetterReturnTypes(Path srcDir) {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (!Files.isDirectory(srcDir)) return result;

        try (Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(file);
                        cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                          .forEach(cls -> {
                              Map<String, String> getters = new HashMap<>();
                              // Collect explicitly declared getter methods
                              for (MethodDeclaration m : cls.getMethods()) {
                                  String name = m.getNameAsString();
                                  if ((name.startsWith("get") && name.length() > 3
                                       || name.startsWith("is") && name.length() > 2)
                                      && m.getParameters().isEmpty()
                                      && !m.getType().isVoidType()) {
                                      getters.put(name, m.getTypeAsString());
                                  }
                              }
                              // Also derive getters from field declarations for Lombok-annotated
                              // classes (@Data / @Getter) whose getters are not in the source AST.
                              boolean hasLombokGetters = cls.getAnnotations().stream()
                                      .anyMatch(a -> {
                                          String n = a.getNameAsString();
                                          return "Data".equals(n) || "Getter".equals(n)
                                                  || "Value".equals(n);
                                      });
                              if (hasLombokGetters) {
                                  for (com.github.javaparser.ast.body.FieldDeclaration f : cls.getFields()) {
                                      String fieldType = f.getElementType().asString();
                                      for (com.github.javaparser.ast.body.VariableDeclarator v : f.getVariables()) {
                                          String fieldName = v.getNameAsString();
                                          // boolean fields → isXxx, others → getXxx
                                          String getterName = ("boolean".equals(fieldType) || "Boolean".equals(fieldType))
                                                  ? "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1)
                                                  : "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                                          getters.putIfAbsent(getterName, fieldType);
                                      }
                                  }
                              }
                              if (!getters.isEmpty()) {
                                  result.put(cls.getNameAsString(), getters);
                              }
                          });
                    } catch (Exception ignored) {}
                });
        } catch (IOException e) {
            log.debug("collectGetterReturnTypes: could not walk {}: {}", srcDir, e.getMessage());
        }
        return result;
    }

    /**
     * Deletes source files for auth-principal types (e.g., {@code User.java}) that are no
     * longer referenced anywhere in the generated service after the rewrite. These files were
     * copied by {@link SharedCodeCopier} as transitive dependencies of controller classes that
     * used {@code @AuthenticationPrincipal User user}, but after rewriting to
     * {@code GatewayPrincipal} they become unreachable dead code.
     */
    private void deleteOrphanedAuthTypes(Path srcDir, Set<String> replacedTypes) throws IOException {
        // Build the set of simple names still actively used across all remaining .java files.
        // Exclude each type's own source file (e.g. User.java when checking for "User") so
        // that the class declaration itself does not count as a "still referenced" signal.
        Set<String> stillReferenced = new HashSet<>();
        try (Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    String fileName = p.getFileName().toString();
                    return replacedTypes.stream().noneMatch(t -> fileName.equals(t + ".java"));
                })
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        for (String type : replacedTypes) {
                            // Use word-boundary matching to avoid false positives from
                            // names like "UsernamePasswordAuthenticationFilter" matching "User"
                            if (Pattern.compile("\\b" + Pattern.quote(type) + "\\b")
                                       .matcher(content).find()) {
                                stillReferenced.add(type);
                            }
                        }
                    } catch (Exception ignored) {}
                });
        }

        // Delete source files for types that are no longer referenced
        for (String type : replacedTypes) {
            if (stillReferenced.contains(type)) continue;
            try (Stream<Path> walk = Files.walk(srcDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(type + ".java"))
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            log.info("Deleted orphaned auth-principal type after GatewayPrincipal rewrite: {}",
                                    file.getFileName());
                        } catch (IOException e) {
                            log.warn("Could not delete orphaned auth type {}: {}", file, e.getMessage());
                        }
                    });
            }
        }
    }
}
