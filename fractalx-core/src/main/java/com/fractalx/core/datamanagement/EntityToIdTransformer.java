package com.fractalx.core.generator;

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

public class EntityToIdTransformer {

    private static final Logger log = LoggerFactory.getLogger(EntityToIdTransformer.class);

    public void transform(Path serviceRoot, FractalModule module) {
        // 1. DYNAMIC DETECTION: No more hardcoding!
        Set<String> remoteEntities = detectRemoteEntities(serviceRoot);

        if (remoteEntities.isEmpty()) {
            log.info("No remote entity references found to transform.");
            return;
        }

        log.info("🔍 Detected Remote Entities: {}", remoteEntities);

        // 2. Scan and Transform Files
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
     * INTELLIGENT DETECTION 🧠
     * 1. Lists all @Entity classes present in this folder (Local).
     * 2. Finds all classes referenced by @ManyToOne/@OneToOne (Candidates).
     * 3. Any Candidate that is NOT Local is considered Remote.
     */
    private Set<String> detectRemoteEntities(Path root) {
        Set<String> localEntities = new HashSet<>();
        Set<String> referencedEntities = new HashSet<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);

                    // A. Register Local Entities
                    if (content.contains("@Entity")) {
                        String className = path.getFileName().toString().replace(".java", "");
                        localEntities.add(className);

                        // B. Find dependencies inside this Entity
                        // Regex looks for: @ManyToOne ... private ClassName fieldName;
                        // We capture "ClassName" (Group 3)
                        String regex = "(?s)(@ManyToOne|@OneToOne|@JoinColumn)[\\s\\S]*?private\\s+(\\w+)\\s+\\w+;";
                        Matcher matcher = Pattern.compile(regex).matcher(content);

                        while (matcher.find()) {
                            String type = matcher.group(2); // The Class Name (e.g., Customer)
                            // Ignore basic Java types just in case
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

        // The Magic: Remote = Referenced - Local
        referencedEntities.removeAll(localEntities);
        return referencedEntities;
    }

    private String removeImports(String content, String entityName) {
        return content.replaceAll("import\\s+.*\\." + entityName + ";", "");
    }

    private String transformEntityField(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // --- STEP 1: BLIND RENAME ---
        String oldFieldRegex = "private\\s+" + entityName + "\\s+" + fieldName + ";";
        String newFieldStr = "private String " + fieldName + "Id;";

        if (content.contains("private " + entityName + " " + fieldName + ";")) {
            content = content.replaceAll(oldFieldRegex, newFieldStr);
        } else {
            content = content.replaceAll("private\\s+" + entityName + "\\s+" + fieldName + "\\s*;", newFieldStr);
        }

        // --- STEP 2: CLEANUP ANNOTATIONS ---
        String manyToOneRegex = "@ManyToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(manyToOneRegex, newFieldStr);

        String joinColumnRegex = "@JoinColumn\\(.*?\\)[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(joinColumnRegex, newFieldStr);

        String oneToOneRegex = "@OneToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(oneToOneRegex, newFieldStr);

        // --- STEP 3: FIX METHODS ---
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