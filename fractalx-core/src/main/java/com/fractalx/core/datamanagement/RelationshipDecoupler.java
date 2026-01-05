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
 * AST-based Relationship Decoupler (JavaParser)
 *
 * Converts cross-service JPA relationships into simple ID fields.
 * - @ManyToOne/@OneToOne/@JoinColumn fields (remote types) -> String <fieldName>Id
 * - @OneToMany List<Remote> -> removed (replaced with comment)
 * Also rewrites some service-layer logic:
 * - Remote x = new Remote(); -> String xId = null;
 * - x.setId(expr); -> xId = expr;
 * - entity.setRemote(x); -> entity.setRemoteId(xId) OR request accessor if available.
 */
public class RelationshipDecoupler {

    private static final Logger log = LoggerFactory.getLogger(RelationshipDecoupler.class);

    private final JavaParser javaParser = new JavaParser();

    // Relationship annotations we treat as "coupling points"
    private static final Set<String> REL_ANNOS = Set.of("ManyToOne", "OneToOne", "JoinColumn");
    private static final String ONE_TO_MANY = "OneToMany";

    public void transform(Path serviceRoot, FractalModule module) {
        try {
            // 1) Build local entity set (what exists in this service)
            Set<String> localEntities = findLocalEntityNames(serviceRoot);

            // 2) Build remote entity set (what this service references in relationships but doesn't own)
            Set<String> remoteEntities = findRemoteEntities(serviceRoot, localEntities);

            if (remoteEntities.isEmpty()) {
                log.info("✅ [Data] No remote entity references found for {}. Data is fully local.", module.getServiceName());
                return;
            }

            log.info("🧩 [Data] Remote entity types to decouple for {}: {}", module.getServiceName(), remoteEntities);

            // 3) Pre-scan Request DTOs (record vs POJO + available id accessors)
            RequestInfoIndex requestIndex = buildRequestIndex(serviceRoot);

            // 4) Walk all Java files and apply AST transformations
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

    // ------------------------------------------------------------
    // Phase 1: Detect local entities
    // ------------------------------------------------------------

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
                    // keep robust; a parse failure shouldn't kill whole pipeline
                }
            });
        }
        return local;
    }

    // ------------------------------------------------------------
    // Phase 2: Detect remote entities referenced by relationships
    // ------------------------------------------------------------

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

                        // Find relationship fields and collect their types
                        for (FieldDeclaration field : c.getFields()) {
                            if (!hasAnyAnnotation(field, REL_ANNOS) && !hasAnnotation(field, ONE_TO_MANY)) continue;

                            // @OneToMany List<X>
                            if (hasAnnotation(field, ONE_TO_MANY)) {
                                field.getVariables().forEach(v -> {
                                    Optional<String> generic = extractGenericTypeName(v.getType());
                                    generic.ifPresent(typeName -> referenced.add(typeName));
                                });
                                continue;
                            }

                            // @ManyToOne/@OneToOne/@JoinColumn private X something;
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

        // Remote = referenced - local - primitives/common
        referenced.removeAll(localEntities);
        referenced.removeAll(Set.of("String", "Long", "Integer", "UUID", "Double", "Float", "Boolean"));
        return referenced;
    }

    // ------------------------------------------------------------
    // Request Index: record vs POJO, available id accessors
    // ------------------------------------------------------------

    private static class RequestInfoIndex {
        // requestTypeName -> info
        final Map<String, RequestInfo> requestInfo = new HashMap<>();
    }

    private static class RequestInfo {
        final boolean isRecord;
        final Set<String> recordComponents = new HashSet<>(); // e.g., customerId
        final Set<String> pojoGetters = new HashSet<>();      // e.g., getCustomerId

        RequestInfo(boolean isRecord) {
            this.isRecord = isRecord;
        }
    }

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

                            // Record
                            for (RecordDeclaration rd : cu.findAll(RecordDeclaration.class)) {
                                String name = rd.getNameAsString();
                                RequestInfo info = new RequestInfo(true);
                                rd.getParameters().forEach(p -> info.recordComponents.add(p.getNameAsString()));
                                idx.requestInfo.put(name, info);
                            }

                            // POJO class
                            for (ClassOrInterfaceDeclaration cd : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                                if (cd.isInterface()) continue;
                                if (!cd.getNameAsString().endsWith("Request")) continue;
                                if (cd.getNameAsString().equals("Request")) continue;

                                String name = cd.getNameAsString();
                                RequestInfo info = new RequestInfo(false);

                                // getters: getXxxId
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

    // ------------------------------------------------------------
    // File Transformation (AST)
    // ------------------------------------------------------------

    private void transformFile(Path javaFile,
                               Set<String> localEntities,
                               Set<String> remoteEntities,
                               RequestInfoIndex requestIndex) throws IOException {

        Optional<CompilationUnit> cuOpt = javaParser.parse(javaFile).getResult();
        if (cuOpt.isEmpty()) return;

        CompilationUnit cu = cuOpt.get();
        boolean modified = false;

        // Remove imports that reference remote entity types (best-effort)
        modified |= removeRemoteImports(cu, remoteEntities);

        // Transform entity classes
        for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (hasAnnotation(c, "Entity")) {
                modified |= transformEntityClass(c, remoteEntities);
            }
        }

        // Transform service logic (non-entity code)
        modified |= transformServiceLogic(cu, remoteEntities, requestIndex);

        if (modified) {
            Files.writeString(javaFile, cu.toString());
            log.info("🔧 [Data] Refactored: {}", javaFile.getFileName());
        }
    }

    // ------------------------------------------------------------
    // Entity Transformations
    // ------------------------------------------------------------

    private boolean transformEntityClass(ClassOrInterfaceDeclaration entityClass, Set<String> remoteEntities) {
        boolean modified = false;

        // Track field renames: oldName -> newName
        Map<String, String> fieldRenameMap = new HashMap<>();

        // 1) Transform @ManyToOne/@OneToOne/@JoinColumn fields referencing remote entity types
        for (FieldDeclaration field : new ArrayList<>(entityClass.getFields())) {

            // Handle OneToMany List<Remote>
            if (hasAnnotation(field, ONE_TO_MANY)) {
                boolean removedAny = removeRemoteOneToManyCollections(entityClass, field, remoteEntities);
                modified |= removedAny;
                continue;
            }

            if (!hasAnyAnnotation(field, REL_ANNOS)) continue;

            for (VariableDeclarator var : field.getVariables()) {
                String typeName = simpleTypeName(var.getType());
                if (typeName == null || !remoteEntities.contains(typeName)) continue;

                String oldFieldName = var.getNameAsString();
                String newFieldName = oldFieldName.endsWith("Id") ? oldFieldName : oldFieldName + "Id";

                // Change type to String
                var.setType("String");

                // Rename variable
                if (!oldFieldName.equals(newFieldName)) {
                    var.setName(newFieldName);
                    fieldRenameMap.put(oldFieldName, newFieldName);
                }

                // Remove relationship annotations from THIS field
                removeAnnotationsByName(field, REL_ANNOS);
                removeAnnotationsByName(field, Set.of(ONE_TO_MANY)); // safety
                // JoinColumn may appear; removed above via REL_ANNOS

                modified = true;
            }
        }

        // 2) Update getters/setters for renamed fields
        if (!fieldRenameMap.isEmpty()) {
            modified |= updateEntityAccessors(entityClass, fieldRenameMap);
            // 3) Update internal references within entity class to renamed fields
            modified |= renameFieldReferences(entityClass, fieldRenameMap);
        }

        return modified;
    }

    private boolean removeRemoteOneToManyCollections(ClassOrInterfaceDeclaration entityClass,
                                                     FieldDeclaration field,
                                                     Set<String> remoteEntities) {
        boolean modified = false;

        // If any variable is List<Remote>, remove the field
        for (VariableDeclarator var : field.getVariables()) {
            Optional<String> generic = extractGenericTypeName(var.getType());
            if (generic.isPresent() && remoteEntities.contains(generic.get())) {
                // Replace the field with a comment line
                // JavaParser doesn't support "comment-only field", so we remove and attach comment near class body.
                field.remove();
                entityClass.addOrphanComment(new LineComment(" Removed remote relationship list: " + generic.get()));
                modified = true;
            }
        }
        return modified;
    }

    private boolean updateEntityAccessors(ClassOrInterfaceDeclaration entityClass, Map<String, String> renameMap) {
        boolean modified = false;

        for (MethodDeclaration m : entityClass.getMethods()) {
            // Getter: getX()
            if (m.getParameters().isEmpty() && m.getNameAsString().startsWith("get")) {
                String suffix = m.getNameAsString().substring(3); // X
                if (suffix.isEmpty()) continue;

                // e.g., getCustomer -> customer
                String guessedField = lowerFirst(suffix);
                if (renameMap.containsKey(guessedField)) {
                    String newField = renameMap.get(guessedField);
                    m.setType("String");
                    m.setName("get" + upperFirst(newField)); // getCustomerId

                    // replace return statements referencing old field
                    m.findAll(ReturnStmt.class).forEach(r -> {
                        r.getExpression().ifPresent(expr -> {
                            if (expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals(guessedField)) {
                                r.setExpression(new NameExpr(newField));
                            }
                            if (expr.isFieldAccessExpr() && expr.asFieldAccessExpr().getNameAsString().equals(guessedField)) {
                                // this.customer -> this.customerId
                                expr.asFieldAccessExpr().setName(newField);
                            }
                        });
                    });

                    modified = true;
                }
            }

            // Setter: setX(...)
            if (m.getNameAsString().startsWith("set") && m.getParameters().size() == 1) {
                String suffix = m.getNameAsString().substring(3);
                if (suffix.isEmpty()) continue;

                String guessedField = lowerFirst(suffix);
                if (renameMap.containsKey(guessedField)) {
                    String newField = renameMap.get(guessedField);

                    m.setName("set" + upperFirst(newField)); // setCustomerId
                    m.getParameter(0).setType("String");
                    m.getParameter(0).setName(newField); // parameter named customerId

                    // update assignments inside setter: this.customer = customer -> this.customerId = customerId
                    m.findAll(AssignExpr.class).forEach(a -> {
                        // left side
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

                        // right side
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

    private boolean renameFieldReferences(ClassOrInterfaceDeclaration entityClass, Map<String, String> renameMap) {
        boolean modified = false;

        // NameExpr occurrences: customer -> customerId
        for (NameExpr ne : entityClass.findAll(NameExpr.class)) {
            String old = ne.getNameAsString();
            if (renameMap.containsKey(old)) {
                ne.setName(renameMap.get(old));
                modified = true;
            }
        }

        // FieldAccessExpr occurrences: this.customer -> this.customerId
        for (FieldAccessExpr fa : entityClass.findAll(FieldAccessExpr.class)) {
            String old = fa.getNameAsString();
            if (renameMap.containsKey(old)) {
                fa.setName(renameMap.get(old));
                modified = true;
            }
        }

        return modified;
    }

    // ------------------------------------------------------------
    // Service Logic Transformations (Non-Entity)
    // ------------------------------------------------------------

    private boolean transformServiceLogic(CompilationUnit cu,
                                          Set<String> remoteEntities,
                                          RequestInfoIndex requestIndex) {
        boolean modified = false;

        // Process each method independently so we can track local variables
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            Set<String> localNames = new HashSet<>();
            Map<String, String> remoteVarToIdVar = new HashMap<>(); // customer -> customerId

            // collect parameter names
            method.getParameters().forEach(p -> localNames.add(p.getNameAsString()));

            // 1) Convert local variables of remote entity type to String <name>Id
            for (VariableDeclarator vd : method.findAll(VariableDeclarator.class)) {
                String typeName = simpleTypeName(vd.getType());
                if (typeName != null && remoteEntities.contains(typeName)) {
                    String oldName = vd.getNameAsString();
                    String newName = oldName.endsWith("Id") ? oldName : oldName + "Id";

                    vd.setType("String");
                    vd.setName(newName);

                    // Replace initializer: new Remote() -> null
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

            // 2) Transform: remoteVar.setId(expr) -> remoteVarId = expr
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

                // Replace the entire statement if it's in an ExpressionStmt
                Optional<Statement> stmtOpt = call.findAncestor(Statement.class);
                if (stmtOpt.isPresent() && stmtOpt.get().isExpressionStmt()) {
                    stmtOpt.get().asExpressionStmt().setExpression(assign);
                    modified = true;
                }
            }

            // 3) Transform: something.setRemote(remoteVar) -> something.setRemoteId(remoteVarId or request accessor)
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                if (!call.getNameAsString().startsWith("set")) continue;
                if (call.getArguments().size() != 1) continue;

                Expression arg = call.getArgument(0);

                // only handle NameExpr argument: setCustomer(customer)
                if (!arg.isNameExpr()) continue;

                String argName = arg.asNameExpr().getNameAsString();

                // If we transformed customer -> customerId var, then rewrite setter + arg
                if (remoteVarToIdVar.containsKey(argName)) {
                    String idVar = remoteVarToIdVar.get(argName);

                    // Rename method: setX -> setXId (only if not already)
                    if (!call.getNameAsString().endsWith("Id")) {
                        call.setName(call.getNameAsString() + "Id");
                    }

                    call.setArgument(0, new NameExpr(idVar));
                    modified = true;
                    continue;
                }

                // If argument is "customer" but we don't have customerId var,
                // try request-based access: request.customerId() or request.getCustomerId()
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

    private Optional<Expression> tryBuildRequestIdAccessor(MethodDeclaration method,
                                                           String setterName,
                                                           RequestInfoIndex requestIndex) {
        // setterName like setCustomer, setPayment, setSupplierWarranty... we build customerId
        if (!setterName.startsWith("set") || setterName.length() <= 3) return Optional.empty();
        String base = lowerFirst(setterName.substring(3)); // customer
        String idName = base.endsWith("Id") ? base : base + "Id"; // customerId

        // Look for a parameter named "request"
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
            // record accessor: request.customerId()
            if (info.recordComponents.contains(idName)) {
                return Optional.of(new MethodCallExpr(new NameExpr("request"), idName));
            }
        } else {
            // pojo getter: request.getCustomerId()
            String getter = "get" + upperFirst(idName);
            if (info.pojoGetters.contains(getter)) {
                return Optional.of(new MethodCallExpr(new NameExpr("request"), getter));
            }
        }

        return Optional.empty();
    }

    // ------------------------------------------------------------
    // Imports
    // ------------------------------------------------------------

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
    // Helpers
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
            return ct.getName().getIdentifier(); // simple name
        }
        return t.asString(); // fallback
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
