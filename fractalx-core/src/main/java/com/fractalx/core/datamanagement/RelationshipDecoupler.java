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
 * - Local Relationships: Kept as-is (Hibernate creates FKs).
 * - Remote Relationships: Converted to IDs (Hibernate creates simple columns).
 */
public class RelationshipDecoupler {

    private static final Logger log = LoggerFactory.getLogger(RelationshipDecoupler.class);

    public void transform(Path serviceRoot, FractalModule module) {
        // 1. Detect entities that are referenced but not present locally
        Set<String> remoteEntities = detectRemoteEntities(serviceRoot);

        if (remoteEntities.isEmpty()) {
            log.info("   ✅ No remote entity references found. Data is fully local.");
            return;
        }

        log.info("   ✂️ Decoupling Remote Entities: {}", remoteEntities);

        // 2. Scan files and apply transformations
        try (Stream<Path> paths = Files.walk(serviceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    String original = content;

                    for (String remoteEntity : remoteEntities) {
                        // A. Remove Imports first
                        content = removeImports(content, remoteEntity);

                        // B. Transform Fields (Entity -> ID)
                        if (content.contains("@Entity")) {
                            content = transformEntityField(content, remoteEntity);
                            content = removeOneToManyCollections(content, remoteEntity); // NEW: Handle Lists
                        }

                        // C. Fix Service Logic / Methods
                        content = transformMethodSignatures(content, remoteEntity);
                        content = transformServiceLogic(content, remoteEntity);
                    }

                    if (!content.equals(original)) {
                        Files.writeString(path, content);
                        log.info("      ✓ Refactored: {}", path.getFileName());
                    }
                } catch (IOException e) {
                    log.error("Failed to transform " + path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk source files", e);
        }
    }

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
                            if (!type.equals("String") && !type.equals("Long") && !type.equals("Integer")) {
                                referencedEntities.add(type);
                            }
                        }
                    }
                } catch (IOException e) { /* ignore */ }
            });
        } catch (IOException e) { /* ignore */ }

        // Remote = Referenced - Local
        referencedEntities.removeAll(localEntities);
        return referencedEntities;
    }

    private String removeImports(String content, String entityName) {
        // Removes: import com.package.Customer;
        return content.replaceAll("import\\s+.*\\." + entityName + ";", "");
    }

    private String transformEntityField(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // 1. Rename field: private Customer customer; -> private String customerId;
        // We use regex to ensure we match whole words and spacing
        String oldFieldRegex = "private\\s+" + entityName + "\\s+" + fieldName + "\\s*;";
        String newFieldStr = "private String " + fieldName + "Id;";

        if (content.contains("private " + entityName + " " + fieldName + ";")) {
            // Simple string replace is safer if exact match exists
            content = content.replace("private " + entityName + " " + fieldName + ";", newFieldStr);
        } else {
            // Fallback to regex for varied spacing
            content = content.replaceAll(oldFieldRegex, newFieldStr);
        }

        // 2. Remove JPA annotations attached to this field
        // We look for annotations followed immediately by our NEW field string
        String manyToOneRegex = "@ManyToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(manyToOneRegex, newFieldStr);

        String joinColumnRegex = "@JoinColumn\\(.*?\\)[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(joinColumnRegex, newFieldStr);

        String oneToOneRegex = "@OneToOne[\\s\\S]*?" + newFieldStr;
        content = content.replaceAll(oneToOneRegex, newFieldStr);

        return content;
    }

    // NEW: Handles @OneToMany(mappedBy="...") private List<RemoteEntity> items;
    // If the other side is remote, this list is useless and breaks compilation. Remove it.
    private String removeOneToManyCollections(String content, String entityName) {
        // Regex: @OneToMany... List<EntityName> ... ;
        String listRegex = "(?s)@OneToMany[\\s\\S]*?List<" + entityName + ">.*?;";
        return content.replaceAll(listRegex, "// Removed remote relationship list: " + entityName);
    }

    // NEW: Updates methods like public void setCustomer(Customer c) -> setCustomerId(String id)
    private String transformMethodSignatures(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // Getter
        content = content.replace("public " + entityName + " get" + entityName + "()",
                "public String get" + entityName + "Id()");
        content = content.replace("return " + fieldName + ";",
                "return " + fieldName + "Id;");

        // Setter
        content = content.replace("public void set" + entityName + "(" + entityName + " " + fieldName + ")",
                "public void set" + entityName + "Id(String " + fieldName + "Id)");
        content = content.replace("this." + fieldName + " = " + fieldName + ";",
                "this." + fieldName + "Id = " + fieldName + "Id;");

        return content;
    }

    private String transformServiceLogic(String content, String entityName) {
        String fieldName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);

        // Remove constructor calls: new Customer()
        content = content.replaceAll(entityName + "\\s+" + fieldName + "\\s*=\\s*new\\s+" + entityName + "\\(\\);", "");

        // Remove setter calls on the object: customer.setId(...)
        content = content.replaceAll(fieldName + "\\.setId\\(.*?\\);", "");

        // Fix logic where we set the relationship
        if (content.contains("set" + entityName + "(")) {
            content = content.replace("set" + entityName + "(" + fieldName + ")",
                    "set" + entityName + "Id(request.get" + entityName + "Id())");
        }
        return content;
    }
}