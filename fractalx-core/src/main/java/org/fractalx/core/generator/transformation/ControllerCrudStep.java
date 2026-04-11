package org.fractalx.core.generator.transformation;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ensures decomposed {@code @RestController} classes expose standard flat CRUD endpoints.
 *
 * <p>When the monolith used nested resource routes (e.g. {@code POST /api/customers/{id}/orders})
 * for entity creation, callers hitting the flat route ({@code POST /api/orders}) through the
 * gateway receive 405. This step adds missing {@code @PostMapping} and {@code @PutMapping}
 * endpoints on the collection / single-resource paths.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Scan every {@code @RestController} in the generated service tree.</li>
 *   <li>Detect entity type from {@code List<EntityType>} getter on the base path.</li>
 *   <li>Ensure the entity's service has a {@code save(EntityType)} method; add one if missing.</li>
 *   <li>Inject {@code POST} and {@code PUT} methods into the controller that call {@code service.save()}.</li>
 * </ol>
 */
public class ControllerCrudStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ControllerCrudStep.class);

    // Detect entity type from "public List<X> methodName()" — collection getter
    private static final Pattern GET_LIST = Pattern.compile(
            "public\\s+List<(\\w+)>\\s+\\w+\\s*\\(\\s*\\)");

    // Detect existing @PostMapping on the collection (no sub-path arg, or empty arg)
    private static final Pattern POST_COLLECTION = Pattern.compile(
            "@PostMapping\\s*(?:\\(\\s*\\)\\s*)?(?=\\s*(?:@\\w|public\\s))");

    // Detect existing @PutMapping on /{id}
    private static final Pattern PUT_ID = Pattern.compile(
            "@PutMapping\\s*\\(\\s*\"[^\"]*\\{id\\}");

    // Detect a service field: "private final XService xService"
    private static final Pattern SERVICE_FIELD = Pattern.compile(
            "(?:private\\s+)?final\\s+(\\w+Service)\\s+(\\w+)");

    // Detect a public save/create method that takes only the entity type as parameter
    private static final Pattern SAVE_METHOD = Pattern.compile(
            "public\\s+(?:\\w+)\\s+(save|create|persist|add)\\s*\\(\\s*(\\w+)\\s+\\w+\\s*\\)");

    @Override
    public void generate(GenerationContext context) throws IOException {
        Path srcMainJava = context.getSrcMainJava();
        if (!Files.exists(srcMainJava)) return;

        try (Stream<Path> stream = Files.walk(srcMainJava)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .forEach(file -> {
                      try { processController(file, srcMainJava); }
                      catch (IOException e) {
                          log.warn("ControllerCrudStep: skipping {}: {}", file.getFileName(), e.getMessage());
                      }
                  });
        }
    }

    private void processController(Path file, Path srcMainJava) throws IOException {
        String src = Files.readString(file);
        if (!src.contains("@RestController")) return;
        if (!src.contains("@RequestMapping")) return;

        // Determine entity type from List<X> getter
        Matcher glm = GET_LIST.matcher(src);
        if (!glm.find()) return;
        String entityType = glm.group(1);

        // Find service field
        Matcher sfm = SERVICE_FIELD.matcher(src);
        if (!sfm.find()) return;
        String serviceClassName = sfm.group(1); // e.g. "OrderService"
        String serviceVar = sfm.group(2);        // e.g. "orderService"

        // Find the service file in the same source tree
        Path serviceFile = findFile(srcMainJava, serviceClassName + ".java");
        if (serviceFile == null) {
            log.debug("ControllerCrudStep: service file not found for {}", serviceClassName);
            return;
        }

        // Ensure service has a save(EntityType) method; add one if missing
        String saveMethodName = ensureServiceSaveMethod(serviceFile, entityType, serviceClassName);
        if (saveMethodName == null) {
            log.debug("ControllerCrudStep: could not determine save method for {} in {}",
                    entityType, serviceClassName);
            return;
        }

        boolean changed = false;

        // Add POST on collection if missing
        if (!POST_COLLECTION.matcher(src).find()) {
            src = ensureImport(src, "org.springframework.web.bind.annotation.PostMapping");
            src = ensureImport(src, "org.springframework.http.HttpStatus");
            src = ensureImport(src, "org.springframework.web.bind.annotation.ResponseStatus");
            src = ensureImport(src, "org.springframework.web.bind.annotation.RequestBody");
            src = injectBeforeLastBrace(src, buildPostMethod(entityType, serviceVar, saveMethodName));
            log.info("  [ControllerCrudStep] Added POST /{} to {}",
                    entityType.toLowerCase() + "s", file.getFileName());
            changed = true;
        }

        // Add PUT /{id} if missing
        if (!PUT_ID.matcher(src).find()) {
            src = ensureImport(src, "org.springframework.web.bind.annotation.PutMapping");
            src = ensureImport(src, "org.springframework.web.bind.annotation.PathVariable");
            src = ensureImport(src, "org.springframework.web.bind.annotation.RequestBody");
            src = injectBeforeLastBrace(src, buildPutMethod(entityType, serviceVar, saveMethodName));
            log.info("  [ControllerCrudStep] Added PUT /{}s/{{id}} to {}",
                    entityType.toLowerCase(), file.getFileName());
            changed = true;
        }

        if (changed) {
            Files.writeString(file, src);
        }
    }

    /**
     * Ensures the service has a {@code save(EntityType entity)} method.
     * If the service already has a method named save/create/persist/add taking only the entity
     * as its parameter, returns that method name. Otherwise injects a simple
     * {@code public EntityType save(EntityType entity) { return repository.save(entity); }}
     * wrapper and returns "save".
     */
    private String ensureServiceSaveMethod(Path serviceFile, String entityType,
                                            String serviceClassName) throws IOException {
        String svc = Files.readString(serviceFile);

        // Check if there's already a simple single-param create/save method for this entity
        Matcher sm = SAVE_METHOD.matcher(svc);
        while (sm.find()) {
            if (sm.group(2).equals(entityType)) {
                return sm.group(1); // e.g. "save", "create"
            }
        }

        // Find repository field (XRepository xRepository)
        Matcher repoMatcher = Pattern.compile(
                "(?:private\\s+)?final\\s+(\\w+Repository)\\s+(\\w+)").matcher(svc);
        if (!repoMatcher.find()) return null;
        String repoVar = repoMatcher.group(2);

        // Inject save() method before the last closing brace
        String saveMethod = """

    /**
     * Saves (creates or updates) a %s entity.
     * Auto-generated by FractalX ControllerCrudStep to support flat REST POST/PUT endpoints.
     */
    public %s save(%s entity) {
        return %s.save(entity);
    }
""".formatted(entityType, entityType, entityType, repoVar);

        svc = injectBeforeLastBrace(svc, saveMethod);
        Files.writeString(serviceFile, svc);
        log.info("  [ControllerCrudStep] Added save({}) method to {}", entityType, serviceClassName);
        return "save";
    }

    private String buildPostMethod(String entityType, String serviceVar, String saveMethod) {
        String varName = lowerFirst(entityType);
        return """

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public %s create%s(@RequestBody %s %s) {
        return %s.%s(%s);
    }
""".formatted(entityType, entityType, entityType, varName, serviceVar, saveMethod, varName);
    }

    private String buildPutMethod(String entityType, String serviceVar, String saveMethod) {
        String varName = lowerFirst(entityType);
        return """

    @PutMapping("/{id}")
    public %s update%s(@PathVariable Long id,
                        @RequestBody %s %s) {
        return %s.%s(%s);
    }
""".formatted(entityType, entityType, entityType, varName, serviceVar, saveMethod, varName);
    }

    private Path findFile(Path root, String fileName) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.getFileName().toString().equals(fileName))
                         .findFirst().orElse(null);
        }
    }

    private String injectBeforeLastBrace(String src, String method) {
        int lastBrace = src.lastIndexOf('}');
        if (lastBrace < 0) return src;
        return src.substring(0, lastBrace) + method + "}\n";
    }

    private String ensureImport(String src, String fqn) {
        if (src.contains(fqn)) return src;
        // Find last import statement
        int lastImport = src.lastIndexOf("\nimport ");
        if (lastImport < 0) return src;
        int lineEnd = src.indexOf('\n', lastImport + 1);
        if (lineEnd < 0) return src;
        return src.substring(0, lineEnd + 1) + "import " + fqn + ";\n" + src.substring(lineEnd + 1);
    }

    private String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
