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
 *   <li>Detect the resource sub-path from {@code @GetMapping("path")} returning {@code List<X>}.</li>
 *   <li>Check if {@code @PostMapping("path")} and {@code @PutMapping("path/{id}")} already exist.</li>
 *   <li>If missing, ensure the service has a {@code save(Entity)} method and inject the endpoints.</li>
 * </ol>
 */
public class ControllerCrudStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ControllerCrudStep.class);

    // Extract sub-path AND entity type: @GetMapping("/path") ... public List<Entity> method()
    // Group 1 = sub-path (e.g. "/orders"), Group 2 = entity type (e.g. "Order")
    private static final Pattern GET_LIST_WITH_PATH = Pattern.compile(
            "@GetMapping\\(\"(/[^\"{}]+)\"\\)\\s*(?:\\n\\s*(?:@[^\\n]+\\n\\s*)*)?public\\s+List<(\\w+)>\\s+\\w+\\s*\\(\\s*\\)");

    // Detect a service field: "final XService xService"
    private static final Pattern SERVICE_FIELD = Pattern.compile(
            "(?:private\\s+)?final\\s+(\\w+Service)\\s+(\\w+)");

    // Detect a public save/create method that takes only the entity type as parameter
    private static final Pattern SAVE_METHOD = Pattern.compile(
            "public\\s+\\w+\\s+(save|create|persist|add)\\s*\\(\\s*(\\w+)\\s+\\w+\\s*\\)");

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

        // Find collection endpoint: @GetMapping("/subpath") returning List<EntityType>
        Matcher glm = GET_LIST_WITH_PATH.matcher(src);
        if (!glm.find()) return;
        String resourcePath = glm.group(1); // e.g. "/orders"
        String entityType   = glm.group(2); // e.g. "Order"

        // Path-specific checks: a nested @PostMapping("/customers/{id}/orders") does NOT
        // count as a flat @PostMapping("/orders"). Only exact-path matches are treated as
        // "already present" — otherwise we'd leave the flat route unreachable (405).
        boolean hasFlatPost = hasMapping(src, "PostMapping", resourcePath);
        boolean hasFlatPut  = hasMapping(src, "PutMapping",  resourcePath + "/{");

        if (hasFlatPost && hasFlatPut) return;

        // Find service field
        Matcher sfm = SERVICE_FIELD.matcher(src);
        if (!sfm.find()) return;
        String serviceClassName = sfm.group(1);
        String serviceVar       = sfm.group(2);

        // Ensure service has a save(EntityType) method
        Path serviceFile = findFile(srcMainJava, serviceClassName + ".java");
        if (serviceFile == null) return;
        String saveMethodName = ensureServiceSaveMethod(serviceFile, entityType, serviceClassName);
        if (saveMethodName == null) return;

        boolean changed = false;

        // Add @PostMapping("/resourcePath") when no flat POST to that path exists
        if (!hasFlatPost) {
            src = ensureImport(src, "org.springframework.web.bind.annotation.PostMapping");
            src = ensureImport(src, "org.springframework.http.HttpStatus");
            src = ensureImport(src, "org.springframework.web.bind.annotation.ResponseStatus");
            src = ensureImport(src, "org.springframework.web.bind.annotation.RequestBody");
            src = injectBeforeLastBrace(src, buildPostMethod(resourcePath, entityType, serviceVar, saveMethodName));
            log.info("  [ControllerCrudStep] Added POST {} to {}", resourcePath, file.getFileName());
            changed = true;
        }

        // Add @PutMapping("/resourcePath/{id}") when no flat PUT to that path exists
        if (!hasFlatPut) {
            src = ensureImport(src, "org.springframework.web.bind.annotation.PutMapping");
            src = ensureImport(src, "org.springframework.web.bind.annotation.PathVariable");
            src = ensureImport(src, "org.springframework.web.bind.annotation.RequestBody");
            src = injectBeforeLastBrace(src, buildPutMethod(resourcePath, entityType, serviceVar, saveMethodName));
            log.info("  [ControllerCrudStep] Added PUT {}/{{id}} to {}", resourcePath, file.getFileName());
            changed = true;
        }

        if (changed) Files.writeString(file, src);
    }

    /**
     * Returns true if the source contains an {@code @<annotation>("<pathPrefix>...")} whose
     * path starts with {@code pathPrefix}. Used to distinguish flat routes from nested ones.
     * <p>Example: {@code hasMapping(src, "PostMapping", "/orders")} matches
     * {@code @PostMapping("/orders")} but NOT {@code @PostMapping("/customers/{id}/orders")}.
     */
    private boolean hasMapping(String src, String annotation, String pathPrefix) {
        // Match: @PostMapping( "/orders" ...) or @PostMapping(value = "/orders" ...)
        // The value must START with pathPrefix (avoids matching nested paths).
        Pattern p = Pattern.compile(
                "@" + annotation + "\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"(" + Pattern.quote(pathPrefix) + "[^\"]*)\"");
        return p.matcher(src).find();
    }

    /**
     * Ensures the service has a {@code save(EntityType entity)} method.
     * Returns the method name to call ({@code "save"} or an existing create/save method name).
     */
    private String ensureServiceSaveMethod(Path serviceFile, String entityType,
                                            String serviceClassName) throws IOException {
        String svc = Files.readString(serviceFile);

        // Check if there's already a single-param create/save method for this entity
        Matcher sm = SAVE_METHOD.matcher(svc);
        while (sm.find()) {
            if (sm.group(2).equals(entityType)) return sm.group(1);
        }

        // Find repository field
        Matcher repoMatcher = Pattern.compile(
                "(?:private\\s+)?final\\s+(\\w+Repository)\\s+(\\w+)").matcher(svc);
        if (!repoMatcher.find()) return null;
        String repoVar = repoMatcher.group(2);

        // Inject save() wrapper
        String saveMethod = """

    /**
     * Saves (creates or updates) a %s entity.
     * Auto-generated by FractalX ControllerCrudStep.
     */
    public %s save(%s entity) {
        return %s.save(entity);
    }
""".formatted(entityType, entityType, entityType, repoVar);

        svc = injectBeforeLastBrace(svc, saveMethod);
        Files.writeString(serviceFile, svc);
        log.info("  [ControllerCrudStep] Added save({}) to {}", entityType, serviceClassName);
        return "save";
    }

    private String buildPostMethod(String path, String entityType,
                                   String serviceVar, String saveMethod) {
        String varName = lowerFirst(entityType);
        return """

    @PostMapping("%s")
    @ResponseStatus(HttpStatus.CREATED)
    public %s create%s(@RequestBody %s %s) {
        return %s.%s(%s);
    }
""".formatted(path, entityType, entityType, entityType, varName, serviceVar, saveMethod, varName);
    }

    private String buildPutMethod(String path, String entityType,
                                  String serviceVar, String saveMethod) {
        String varName = lowerFirst(entityType);
        return """

    @PutMapping("%s/{id}")
    public %s update%s(@PathVariable Long id,
                        @RequestBody %s %s) {
        return %s.%s(%s);
    }
""".formatted(path, entityType, entityType, entityType, varName, serviceVar, saveMethod, varName);
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
