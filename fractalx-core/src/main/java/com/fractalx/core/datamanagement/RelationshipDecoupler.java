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
 * Transforms Java Entities to enforce Microservice Data Isolation.
 * Converts relationships to remote entities into simple ID fields.
 * Handles both Java Classes and Records for accessor methods.
 */
public class RelationshipDecoupler {

    private static final Logger log = LoggerFactory.getLogger(RelationshipDecoupler.class);

    public void transform(Path serviceRoot, FractalModule module) {
        // Detect entities that are referenced but not present locally
        Set<String> remoteEntities = detectRemoteEntities(serviceRoot);

        if (remoteEntities.isEmpty()) {
            log.info("No remote entity references found. Data is fully local.");
            return;
        }

        log.info("Decoupling Remote Entities: {}", remoteEntities);

        // Scan files and apply transformations
        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    String original = content;

                    for (String remoteEntity : remoteEntities) {
                        content = removeImports(content, remoteEntity);
                        content = transformEntityField(content, remoteEntity);

                        // Clean up List collections for remote entities
                        if (content.contains("@Entity")) {
                            content = removeOneToManyCollections(content, remoteEntity);
                        }

                        content = transformMethodSignatures(content, remoteEntity);
                        content = transformServiceLogic(content, remoteEntity, serviceRoot);
                    }

                    if (!content.equals(original)) {
                        Files.writeString(path, content);
                        log.info("Refactored: {}", path.getFileName());
                    }
                } catch (IOException e) {
                    log.error("Failed to transform file: " + path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk source files during transformation", e);
        }
    }

    private Set<String> detectRemoteEntities(Path root) {
        Set<String> localEntities = new HashSet<>();
        Set<String> referencedEntities = new HashSet<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    if (content.contains("@Entity")) {
                        String className = path.getFileName().toString().replace(".java", "");
                        localEntities.add(className);

                        // Find dependencies inside this Entity
                        String regex = "(?s)(@ManyToOne|@OneToOne|@JoinColumn)[\\s\\S]*?private\\s+(\\w+)\\s+\\w+;";
                        Matcher matcher = Pattern.compile(regex).matcher(content);

                        while (matcher.find()) {
                            String type = matcher.group(2);
                            if (!type.equals("String") && !type.equals("Long") && !type.equals("Integer")) {
                                referencedEntities.add(type);
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to read file during entity detection: " + path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk source files during entity detection", e);
        }
        referencedEntities.removeAll(localEntities);
        return referencedEntities;
    }

    private String removeImports(String content, String entityName) {
        return content.replaceAll("import\\s+.*\\." + entityName + ";", "");
    }

    private String transformEntityField(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // Rename field from Object to ID
        String oldFieldRegex = "private\\s+" + entityName + "\\s+" + fieldName + "\\s*;";
        String newFieldStr = "private String " + fieldName + "Id;";

        if (content.contains("private " + entityName + " " + fieldName + ";")) {
            content = content.replace("private " + entityName + " " + fieldName + ";", newFieldStr);
        } else {
            content = content.replaceAll(oldFieldRegex, newFieldStr);
        }

        // Remove JPA annotations
        String manyToOneRegex = "@ManyToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(manyToOneRegex, newFieldStr);

        String joinColumnRegex = "@JoinColumn\\(.*?\\)[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(joinColumnRegex, newFieldStr);

        String oneToOneRegex = "@OneToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(oneToOneRegex, newFieldStr);

        return content;
    }

    private String removeOneToManyCollections(String content, String entityName) {
        String listRegex = "(?s)@OneToMany[\\s\\S]*?List<" + entityName + ">.*?;";
        return content.replaceAll(listRegex, "// Removed remote relationship list: " + entityName);
    }

    private String transformMethodSignatures(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // Update Getter
        content = content.replace("public " + entityName + " get" + entityName + "()",
                "public String get" + entityName + "Id()");
        content = content.replace("return " + fieldName + ";",
                "return " + fieldName + "Id;");

        // Update Setter
        content = content.replace("public void set" + entityName + "(" + entityName + " " + fieldName + ")",
                "public void set" + entityName + "Id(String " + fieldName + "Id)");
        content = content.replace("this." + fieldName + " = " + fieldName + ";",
                "this." + fieldName + "Id = " + fieldName + "Id;");

        return content;
    }

    private String transformServiceLogic(String content, String entityName, Path serviceRoot) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // Remove constructor calls
        content = content.replaceAll(entityName + "\\s+" + fieldName + "\\s*=\\s*new\\s+" + entityName + "\\(\\);", "");

        // Remove setter calls on the object itself
        content = content.replaceAll(fieldName + "\\.setId\\(.*?\\);", "");

        // Update set methods to use IDs, checking if Request object is a Record or POJO
        if (content.contains("set" + entityName + "(")) {
            boolean isRecord = checkIfRequestIsRecord(serviceRoot);

            String accessor = isRecord
                    ? "request." + fieldName + "Id()"       // Record style
                    : "request.get" + entityName + "Id()";  // POJO style

            content = content.replace("set" + entityName + "(" + fieldName + ")",
                    "set" + entityName + "Id(" + accessor + ")");
        }
        return content;
    }

    private boolean checkIfRequestIsRecord(Path serviceRoot) {
        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            return paths.filter(p -> p.toString().endsWith("Request.java"))
                    .anyMatch(path -> {
                        try {
                            return Files.readString(path).contains("public record");
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }
}