package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects dependencies on entities that do not exist in the local module
 * and replaces those object references with ID fields (Decoupling).
 */
public class RelationshipDecoupler {

    private static final Logger log = LoggerFactory.getLogger(RelationshipDecoupler.class);

    public void transform(Path serviceRoot, FractalModule module) {
        // 1. Detect entities that are referenced but not present locally
        Set<String> remoteEntities = detectRemoteEntities(serviceRoot);

        if (remoteEntities.isEmpty()) {
            log.info("No remote entity references found to transform.");
            return;
        }

        log.info("🔍 Detected Remote Entities: {}", remoteEntities);

        // 2. Scan files and apply transformations
        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    String original = content;

                    for (String remoteEntity : remoteEntities) {
                        content = removeImports(content, remoteEntity);

                        if (content.contains("@Entity")) {
                            content = transformEntityField(content, remoteEntity);
                        } else {
                            content = transformServiceLogic(content, remoteEntity);
                        }
                    }

                    if (!content.equals(original)) {
                        Files.writeString(path, content);
                        log.info("✓ Patched file: {}", path.getFileName());
                    }
                } catch (IOException e) {
                    log.error("Failed to transform " + path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk source files", e);
        }
    }

    /**
     * Identifies "Remote" entities by comparing all referenced entities
     * against the list of entities actually defined in this service.
     */
    private Set<String> detectRemoteEntities(Path root) {
        Set<String> localEntities = new HashSet<>();
        Set<String> referencedEntities = new HashSet<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);

                    // Register Local Entities
                    if (content.contains("@Entity")) {
                        String className = path.getFileName().toString().replace(".java", "");
                        localEntities.add(className);

                        // Find dependencies inside this Entity
                        String regex = "(?s)(@ManyToOne|@OneToOne|@JoinColumn)[\\s\\S]*?private\\s+(\\w+)\\s+\\w+;";
                        Matcher matcher = Pattern.compile(regex).matcher(content);

                        while (matcher.find()) {
                            String type = matcher.group(2);
                            // Ignore basic Java types
                            if (!type.equals("String") && !type.equals("Long") && !type.equals("Integer")) {
                                referencedEntities.add(type);
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("Error scanning file: " + path, e);
                }
            });
        } catch (IOException e) {
            log.error("Error walking files", e);
        }

        // Remote = Referenced - Local
        referencedEntities.removeAll(localEntities);
        return referencedEntities;
    }

    private String removeImports(String content, String entityName) {
        return content.replaceAll("import\\s+.*\\." + entityName + ";", "");
    }

    private String transformEntityField(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // 1. Rename field (Entity -> String ID)
        String oldFieldRegex = "private\\s+" + entityName + "\\s+" + fieldName + ";";
        String newFieldStr = "private String " + fieldName + "Id;";

        if (content.contains("private " + entityName + " " + fieldName + ";")) {
            content = content.replaceAll(oldFieldRegex, newFieldStr);
        } else {
            content = content.replaceAll("private\\s+" + entityName + "\\s+" + fieldName + "\\s*;", newFieldStr);
        }

        // 2. Remove JPA annotations for the relationship
        String manyToOneRegex = "@ManyToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(manyToOneRegex, newFieldStr);

        String joinColumnRegex = "@JoinColumn\\(.*?\\)[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(joinColumnRegex, newFieldStr);

        String oneToOneRegex = "@OneToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(oneToOneRegex, newFieldStr);

        // 3. Update Getters and Setters
        content = content.replace("public " + entityName + " get" + entityName + "()",
                "public String get" + entityName + "Id()");
        content = content.replace("return " + fieldName + ";",
                "return " + fieldName + "Id;");
        content = content.replace("public void set" + entityName + "(" + entityName + " " + fieldName + ")",
                "public void set" + entityName + "Id(String " + fieldName + "Id)");
        content = content.replace("this." + fieldName + " = " + fieldName + ";",
                "this." + fieldName + "Id = " + fieldName + "Id;");

        return content;
    }

    private String transformServiceLogic(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        content = content.replaceAll(entityName + "\\s+" + fieldName + "\\s*=\\s*new\\s+" + entityName + "\\(\\);", "");
        content = content.replaceAll(fieldName + "\\.setId\\(.*?\\);", "");

        if (content.contains("order.set" + entityName + "(" + fieldName + ")")) {
            content = content.replace("order.set" + entityName + "(" + fieldName + ")",
                    "order.set" + entityName + "Id(request." + fieldName + "Id())");
        }
        return content;
    }
}