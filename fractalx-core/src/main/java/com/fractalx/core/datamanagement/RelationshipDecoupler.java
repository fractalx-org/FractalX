package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.ReturnStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Performs AST-based decoupling of cross-service relationships.
 * Converts JPA relationship fields to String ID fields and updates associated service logic.
 * Handles both POJO and Record request DTOs.
 */
public class RelationshipDecoupler {

    private static final Logger log = LoggerFactory.getLogger(RelationshipDecoupler.class);

    private final JavaParser javaParser = new JavaParser();

    private static final Set<String> REL_ANNOS = Set.of("ManyToOne", "OneToOne", "JoinColumn");
    private static final String ONE_TO_MANY = "OneToMany";

    /**
     * Orchestrates the transformation process: identifies local/remote entities,
     * indexes request DTOs, and applies AST modifications to source files.
     */
    public void transform(Path serviceRoot, FractalModule module) {
        try {
            // Identify entities defined within this service
            Set<String> localEntities = findLocalEntityNames(serviceRoot);

            // Identify entities referenced but not defined locally
            Set<String> remoteEntities = findRemoteEntities(serviceRoot, localEntities);

            if (remoteEntities.isEmpty()) {
                log.info("✅ [Data] No remote entity references found for {}. Data is fully local.", module.getServiceName());
                return;
            }

            log.info("🧩 [Data] Remote entity types to decouple for {}: {}", module.getServiceName(), remoteEntities);

            // Build index of Request objects to determine accessor style (Record vs Class)
            RequestInfoIndex requestIndex = buildRequestIndex(serviceRoot);

            // Process all Java files
            try (Stream<Path> paths = Files.walk(serviceRoot)) {
                paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                    try {
                        transformFile(path, localEntities, remoteEntities, requestIndex);
                    } catch (Exception e) {
                        log.error("❌ [Data] Failed to transform file: {}", path, e);
                    }
                });
            }

        } catch (IOException e) {
            log.error("❌ [Data] Relationship decoupling failed during file traversal", e);
        }
    }

    /**
     * Scans source files to identify @Entity classes defined locally.
     */
    private Set<String> findLocalEntityNames(Path root) throws IOException {
        Set<String> local = new HashSet<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    Optional<CompilationUnit> cuOpt = javaParser.parse(path).getResult();
                    if (cuOpt.isEmpty()) return;
                    CompilationUnit cu = cuOpt.get();

                    for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (hasAnnotation(c, "Entity")) {
                            local.add(c.getNameAsString());
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
        return local;
    }

    /**
     * Identifies entity types used in relationships that are not present in the local entity set.
     */
    private Set<String> findRemoteEntities(Path root, Set<String> localEntities) throws IOException {
        Set<String> referenced = new HashSet<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    Optional<CompilationUnit> cuOpt = javaParser.parse(path).getResult();
                    if (cuOpt.isEmpty()) return;
                    CompilationUnit cu = cuOpt.get();

                    for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (!hasAnnotation(c, "Entity")) continue;

                        for (FieldDeclaration field : c.getFields()) {
                            if (!hasAnyAnnotation(field, REL_ANNOS) && !hasAnnotation(field, ONE_TO_MANY)) continue;

                            // Collect types from @OneToMany List<T>
                            if (hasAnnotation(field, ONE_TO_MANY)) {
                                field.getVariables().forEach(v -> {
                                    Optional<String> generic = extractGenericTypeName(v.getType());
                                    generic.ifPresent(typeName -> referenced.add(typeName));
                                });
                                continue;
                            }

                            // Collect types from single relationships
                            field.getVariables().forEach(v -> {
                                String typeName = simpleTypeName(v.getType());
                                if (typeName != null) referenced.add(typeName);
                            });
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }

        // Filter out local entities and standard Java types
        referenced.removeAll(localEntities);
        referenced.removeAll(Set.of("String", "Long", "Integer", "UUID", "Double", "Float", "Boolean"));
        return referenced;
    }

    // Stores structure of Request objects to support correct code generation
    private static class RequestInfoIndex {
        final Map<String, RequestInfo> requestInfo = new HashMap<>();
    }

    private static class RequestInfo {
        final boolean isRecord;
        final Set<String> recordComponents = new HashSet<>();
        final Set<String> pojoGetters = new HashSet<>();

        RequestInfo(boolean isRecord) {
            this.isRecord = isRecord;
        }
    }

    /**
     * Builds an index of Request classes to determine if they are Records or POJOs
     * and capture available fields/getters.
     */
    private RequestInfoIndex buildRequestIndex(Path serviceRoot) throws IOException {
        RequestInfoIndex idx = new RequestInfoIndex();

        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.getFileName().toString().endsWith("Request.java"))
                    .forEach(path -> {
                        try {
                            Optional<CompilationUnit> cuOpt = javaParser.parse(path).getResult();
                            if (cuOpt.isEmpty()) return;
                            CompilationUnit cu = cuOpt.get();

                            // Process Records
                            for (RecordDeclaration rd : cu.findAll(RecordDeclaration.class)) {
                                String name = rd.getNameAsString();
                                RequestInfo info = new RequestInfo(true);
                                rd.getParameters().forEach(p -> info.recordComponents.add(p.getNameAsString()));
                                idx.requestInfo.put(name, info);
                            }

                            // Process POJO Classes
                            for (ClassOrInterfaceDeclaration cd : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                                if (cd.isInterface()) continue;
                                if (!cd.getNameAsString().endsWith("Request")) continue;
                                if (cd.getNameAsString().equals("Request")) continue;

                                String name = cd.getNameAsString();
                                RequestInfo info = new RequestInfo(false);

                                for (MethodDeclaration m : cd.getMethods()) {
                                    if (m.getNameAsString().startsWith("get")) {
                                        info.pojoGetters.add(m.getNameAsString());
                                    }
                                }
                                idx.requestInfo.put(name, info);
                            }

                        } catch (Exception ignored) {
                        }
                    });
        }
        return idx;
    }

    /**
     * Applies AST transformations to a single Java file: removes imports,
     * transforms entities, and updates service logic.
     */
    private void transformFile(Path javaFile,
                               Set<String> localEntities,
                               Set<String> remoteEntities,
                               RequestInfoIndex requestIndex) throws IOException {

        Optional<CompilationUnit> cuOpt = javaParser.parse(javaFile).getResult();
        if (cuOpt.isEmpty()) return;

        CompilationUnit cu = cuOpt.get();
        boolean modified = false;

        modified |= removeRemoteImports(cu, remoteEntities);

        for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (hasAnnotation(c, "Entity")) {
                modified |= transformEntityClass(c, remoteEntities);
            }
        }

        modified |= transformServiceLogic(cu, remoteEntities, requestIndex);

        if (modified) {
            Files.writeString(javaFile, cu.toString());
            log.info("🔧 [Data] Refactored: {}", javaFile.getFileName());
        }
    }

    /**
     * Modifies entity classes: converts relationship fields to ID fields and updates accessors.
     */
    private boolean transformEntityClass(ClassOrInterfaceDeclaration entityClass, Set<String> remoteEntities) {
        boolean modified = false;
        Map<String, String> fieldRenameMap = new HashMap<>();

        for (FieldDeclaration field : new ArrayList<>(entityClass.getFields())) {

            // Remove OneToMany lists referencing remote entities
            if (hasAnnotation(field, ONE_TO_MANY)) {
                boolean removedAny = removeRemoteOneToManyCollections(entityClass, field, remoteEntities);
                modified |= removedAny;
                continue;
            }

            if (!hasAnyAnnotation(field, REL_ANNOS)) continue;

            // Convert entity references to String ID fields
            for (VariableDeclarator var : field.getVariables()) {
                String typeName = simpleTypeName(var.getType());
                if (typeName == null || !remoteEntities.contains(typeName)) continue;

                String oldFieldName = var.getNameAsString();
                String newFieldName = oldFieldName.endsWith("Id") ? oldFieldName : oldFieldName + "Id";

                var.setType("String");

                if (!oldFieldName.equals(newFieldName)) {
                    var.setName(newFieldName);
                    fieldRenameMap.put(oldFieldName, newFieldName);
                }

                removeAnnotationsByName(field, REL_ANNOS);
                removeAnnotationsByName(field, Set.of(ONE_TO_MANY));

                modified = true;
            }
        }

        if (!fieldRenameMap.isEmpty()) {
            modified |= updateEntityAccessors(entityClass, fieldRenameMap);
            modified |= renameFieldReferences(entityClass, fieldRenameMap);
        }

        return modified;
    }

    /**
     * Removes list fields annotated with @OneToMany if they reference a remote entity.
     */
    private boolean removeRemoteOneToManyCollections(ClassOrInterfaceDeclaration entityClass,
                                                     FieldDeclaration field,
                                                     Set<String> remoteEntities) {
        boolean modified = false;

        for (VariableDeclarator var : field.getVariables()) {
            Optional<String> generic = extractGenericTypeName(var.getType());
            if (generic.isPresent() && remoteEntities.contains(generic.get())) {
                field.remove();
                entityClass.addOrphanComment(new LineComment(" Removed remote relationship list: " + generic.get()));
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Updates getter and setter signatures and bodies to match renamed ID fields.
     */
    private boolean updateEntityAccessors(ClassOrInterfaceDeclaration entityClass, Map<String, String> renameMap) {
        boolean modified = false;

        for (MethodDeclaration m : entityClass.getMethods()) {
            // Update Getter
            if (m.getParameters().isEmpty() && m.getNameAsString().startsWith("get")) {
                String suffix = m.getNameAsString().substring(3);
                if (suffix.isEmpty()) continue;

                String guessedField = lowerFirst(suffix);
                if (renameMap.containsKey(guessedField)) {
                    String newField = renameMap.get(guessedField);
                    m.setType("String");
                    m.setName("get" + upperFirst(newField));

                    m.findAll(ReturnStmt.class).forEach(r -> {
                        r.getExpression().ifPresent(expr -> {
                            if (expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals(guessedField)) {
                                r.setExpression(new NameExpr(newField));
                            }
                            if (expr.isFieldAccessExpr() && expr.asFieldAccessExpr().getNameAsString().equals(guessedField)) {
                                expr.asFieldAccessExpr().setName(newField);
                            }
                        });
                    });

                    modified = true;
                }
            }

            // Update Setter
            if (m.getNameAsString().startsWith("set") && m.getParameters().size() == 1) {
                String suffix = m.getNameAsString().substring(3);
                if (suffix.isEmpty()) continue;

                String guessedField = lowerFirst(suffix);
                if (renameMap.containsKey(guessedField)) {
                    String newField = renameMap.get(guessedField);

                    m.setName("set" + upperFirst(newField));
                    m.getParameter(0).setType("String");
                    m.getParameter(0).setName(newField);

                    m.findAll(AssignExpr.class).forEach(a -> {
                        Expression target = a.getTarget();
                        if (target.isFieldAccessExpr()) {
                            FieldAccessExpr fa = target.asFieldAccessExpr();
                            if (fa.getNameAsString().equals(guessedField)) {
                                fa.setName(newField);
                            }
                        } else if (target.isNameExpr()) {
                            if (target.asNameExpr().getNameAsString().equals(guessedField)) {
                                a.setTarget(new NameExpr(newField));
                            }
                        }

                        Expression value = a.getValue();
                        if (value.isNameExpr() && value.asNameExpr().getNameAsString().equals(guessedField)) {
                            a.setValue(new NameExpr(newField));
                        }
                    });

                    modified = true;
                }
            }
        }

        return modified;
    }

    /**
     * Updates internal field references (this.field -> this.fieldId).
     */
    private boolean renameFieldReferences(ClassOrInterfaceDeclaration entityClass, Map<String, String> renameMap) {
        boolean modified = false;

        for (NameExpr ne : entityClass.findAll(NameExpr.class)) {
            String old = ne.getNameAsString();
            if (renameMap.containsKey(old)) {
                ne.setName(renameMap.get(old));
                modified = true;
            }
        }

        for (FieldAccessExpr fa : entityClass.findAll(FieldAccessExpr.class)) {
            String old = fa.getNameAsString();
            if (renameMap.containsKey(old)) {
                fa.setName(renameMap.get(old));
                modified = true;
            }
        }

        return modified;
    }

    /**
     * Updates service logic to handle ID fields instead of object references.
     * Handles variable instantiation, setId calls, and setEntity calls.
     */
    private boolean transformServiceLogic(CompilationUnit cu,
                                          Set<String> remoteEntities,
                                          RequestInfoIndex requestIndex) {
        boolean modified = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            Set<String> localNames = new HashSet<>();
            Map<String, String> remoteVarToIdVar = new HashMap<>();

            method.getParameters().forEach(p -> localNames.add(p.getNameAsString()));

            // 1) Update local variable declarations (Entity e -> String eId)
            for (VariableDeclarator vd : method.findAll(VariableDeclarator.class)) {
                String typeName = simpleTypeName(vd.getType());
                if (typeName != null && remoteEntities.contains(typeName)) {
                    String oldName = vd.getNameAsString();
                    String newName = oldName.endsWith("Id") ? oldName : oldName + "Id";

                    vd.setType("String");
                    vd.setName(newName);

                    if (vd.getInitializer().isPresent() && vd.getInitializer().get().isObjectCreationExpr()) {
                        ObjectCreationExpr oce = vd.getInitializer().get().asObjectCreationExpr();
                        String createdType = simpleTypeName(oce.getType());
                        if (createdType != null && remoteEntities.contains(createdType)) {
                            vd.setInitializer(new NullLiteralExpr());
                        }
                    }

                    remoteVarToIdVar.put(oldName, newName);
                    localNames.add(newName);

                    modified = true;
                }
            }

            // 2) Update setId calls (e.setId(x) -> eId = x)
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                if (call.getScope().isEmpty()) continue;
                if (!call.getNameAsString().equals("setId")) continue;
                if (call.getArguments().size() != 1) continue;

                Expression scope = call.getScope().get();
                if (!scope.isNameExpr()) continue;

                String varName = scope.asNameExpr().getNameAsString();
                if (!remoteVarToIdVar.containsKey(varName)) continue;

                String idVar = remoteVarToIdVar.get(varName);
                AssignExpr assign = new AssignExpr(new NameExpr(idVar), call.getArgument(0), AssignExpr.Operator.ASSIGN);

                Optional<Statement> stmtOpt = call.findAncestor(Statement.class);
                if (stmtOpt.isPresent() && stmtOpt.get().isExpressionStmt()) {
                    stmtOpt.get().asExpressionStmt().setExpression(assign);
                    modified = true;
                }
            }

            // 3) Update setter calls (setEntity(e) -> setEntityId(eId) or setEntityId(request.getId()))
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                if (!call.getNameAsString().startsWith("set")) continue;
                if (call.getArguments().size() != 1) continue;

                Expression arg = call.getArgument(0);
                if (!arg.isNameExpr()) continue;

                String argName = arg.asNameExpr().getNameAsString();

                // Case A: Variable was renamed locally
                if (remoteVarToIdVar.containsKey(argName)) {
                    String idVar = remoteVarToIdVar.get(argName);

                    if (!call.getNameAsString().endsWith("Id")) {
                        call.setName(call.getNameAsString() + "Id");
                    }

                    call.setArgument(0, new NameExpr(idVar));
                    modified = true;
                    continue;
                }

                // Case B: Try to resolve ID from Request object
                Optional<Expression> requestAccessor = tryBuildRequestIdAccessor(method, call.getNameAsString(), requestIndex);
                if (requestAccessor.isPresent()) {
                    if (!call.getNameAsString().endsWith("Id")) {
                        call.setName(call.getNameAsString() + "Id");
                    }
                    call.setArgument(0, requestAccessor.get());
                    modified = true;
                }
            }
        }

        return modified;
    }

    /**
     * Attempts to build an ID accessor (e.g. request.getCustomerId()) based on the setter name and request type.
     */
    private Optional<Expression> tryBuildRequestIdAccessor(MethodDeclaration method,
                                                           String setterName,
                                                           RequestInfoIndex requestIndex) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) return Optional.empty();
        String base = lowerFirst(setterName.substring(3));
        String idName = base.endsWith("Id") ? base : base + "Id";

        Optional<Parameter> requestParamOpt = method.getParameters().stream()
                .filter(p -> p.getNameAsString().equals("request"))
                .findFirst();
        if (requestParamOpt.isEmpty()) return Optional.empty();

        Parameter requestParam = requestParamOpt.get();
        String requestType = simpleTypeName(requestParam.getType());
        if (requestType == null) return Optional.empty();

        RequestInfo info = requestIndex.requestInfo.get(requestType);
        if (info == null) return Optional.empty();

        if (info.isRecord) {
            if (info.recordComponents.contains(idName)) {
                return Optional.of(new MethodCallExpr(new NameExpr("request"), idName));
            }
        } else {
            String getter = "get" + upperFirst(idName);
            if (info.pojoGetters.contains(getter)) {
                return Optional.of(new MethodCallExpr(new NameExpr("request"), getter));
            }
        }

        return Optional.empty();
    }

    /**
     * Removes imports for remote entity types.
     */
    private boolean removeRemoteImports(CompilationUnit cu, Set<String> remoteEntities) {
        boolean modified = false;

        List<ImportDeclaration> toRemove = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            String last = imp.getName().getIdentifier();
            if (remoteEntities.contains(last)) {
                toRemove.add(imp);
            }
        }

        for (ImportDeclaration imp : toRemove) {
            cu.getImports().remove(imp);
            modified = true;
        }
        return modified;
    }

    // ------------------------------------------------------------
    // Utility Methods
    // ------------------------------------------------------------

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String annoSimpleName) {
        return node.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals(annoSimpleName));
    }

    private boolean hasAnyAnnotation(NodeWithAnnotations<?> node, Set<String> annoNames) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (annoNames.contains(a.getNameAsString())) return true;
        }
        return false;
    }

    private void removeAnnotationsByName(NodeWithAnnotations<?> node, Set<String> annoNames) {
        NodeList<AnnotationExpr> anns = node.getAnnotations();
        anns.removeIf(a -> annoNames.contains(a.getNameAsString()));
    }

    private String simpleTypeName(Type t) {
        if (t.isPrimitiveType()) return null;
        if (t.isArrayType()) return simpleTypeName(t.asArrayType().getComponentType());
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType ct = t.asClassOrInterfaceType();
            return ct.getName().getIdentifier();
        }
        return t.asString();
    }

    private Optional<String> extractGenericTypeName(Type t) {
        if (!t.isClassOrInterfaceType()) return Optional.empty();
        ClassOrInterfaceType ct = t.asClassOrInterfaceType();
        if (ct.getTypeArguments().isEmpty()) return Optional.empty();
        NodeList<Type> args = ct.getTypeArguments().get();
        if (args.isEmpty()) return Optional.empty();
        String name = simpleTypeName(args.get(0));
        return Optional.ofNullable(name);
    }

    private String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String upperFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}