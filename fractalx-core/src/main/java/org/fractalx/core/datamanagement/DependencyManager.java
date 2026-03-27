package org.fractalx.core.datamanagement;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Provisions required infrastructure libraries (Redis, MySQL)
 * by injecting dependencies directly into the service's pom.xml.
 */
public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

//    public void provisionRedis(FractalModule module, Path serviceRoot) {
//        String dependency = """
//        <dependency>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-data-redis</artifactId>
//                </dependency>""";
//
//        injectDependency(module, serviceRoot, "spring-boot-starter-data-redis", dependency);
//    }

    public void provisionMySQL(FractalModule module, Path serviceRoot) {
        String dependency = """
        <dependency>
                    <groupId>com.mysql</groupId>
                    <artifactId>mysql-connector-j</artifactId>
                    <scope>runtime</scope>
                </dependency>""";

        injectDependency(module, serviceRoot, "mysql-connector-j", dependency);
    }

    public void provisionPostgreSQL(FractalModule module, Path serviceRoot) {
        String dependency = """
        <dependency>
                    <groupId>org.postgresql</groupId>
                    <artifactId>postgresql</artifactId>
                    <scope>runtime</scope>
                </dependency>""";
        injectDependency(module, serviceRoot, "postgresql", dependency);
    }

    public void provisionAop(FractalModule module, Path serviceRoot) {
        String dependency = """
        <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-aop</artifactId>
                </dependency>""";
        injectDependency(module, serviceRoot, "spring-boot-starter-aop", dependency);
    }

    /**
     * Walks all {@code .java} files under {@code serviceRoot/src/main/java} and provisions
     * any implied dependencies that are referenced in import statements but not yet present
     * in {@code pom.xml}.
     *
     * <p>Currently detects:
     * <ul>
     *   <li>Lombok ({@code import lombok.*}) → {@code org.projectlombok:lombok}</li>
     *   <li>Jakarta Validation ({@code import jakarta.validation.*}) →
     *       {@code spring-boot-starter-validation}</li>
     * </ul>
     */
    public void provisionImpliedDependencies(FractalModule module, Path serviceRoot) {
        Path srcMainJava = serviceRoot.resolve("src/main/java");
        if (!Files.exists(srcMainJava)) return;

        boolean needsLombok     = false;
        boolean needsValidation = false;

        try (Stream<Path> files = Files.walk(srcMainJava)) {
            for (Path javaFile : files.filter(p -> p.toString().endsWith(".java"))
                                      .toList()) {
                String content = Files.readString(javaFile);
                if (content.contains("import lombok.")) needsLombok     = true;
                if (content.contains("import jakarta.validation.")) needsValidation = true;
                if (needsLombok && needsValidation) break;
            }
        } catch (IOException e) {
            log.error("Failed to scan sources for implied dependencies in {}", module.getServiceName(), e);
            return;
        }

        if (needsLombok) {
            String lombokXml = """
        <dependency>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                    <optional>true</optional>
                </dependency>""";
            injectDependency(module, serviceRoot, "org.projectlombok", lombokXml);
            // Also wire Lombok as an annotation processor so it works without spring-boot-starter-parent.
            injectLombokAnnotationProcessor(module, serviceRoot);
            log.info("   ✓ Provisioned Lombok for {}", module.getServiceName());
        }

        if (needsValidation) {
            String validationXml = """
        <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-validation</artifactId>
                </dependency>""";
            injectDependency(module, serviceRoot, "spring-boot-starter-validation", validationXml);
            log.info("   ✓ Provisioned spring-boot-starter-validation for {}", module.getServiceName());
        }
    }

    /**
     * Injects Lombok into the {@code <annotationProcessorPaths>} of {@code maven-compiler-plugin}
     * so that Lombok annotation processing works when using {@code spring-boot-dependencies} as a
     * BOM (rather than {@code spring-boot-starter-parent} which wires it automatically).
     *
     * <p>{@code annotationProcessorPaths} does NOT resolve versions from
     * {@code <dependencyManagement>}, so an explicit Lombok version is required.
     * The version is derived from the {@code spring-boot.version} property in the pom.
     */
    private void injectLombokAnnotationProcessor(FractalModule module, Path serviceRoot) {
        try {
            Path pomPath = serviceRoot.resolve("pom.xml");
            if (!Files.exists(pomPath)) return;

            String content = Files.readString(pomPath);
            if (content.contains("annotationProcessorPaths")) return; // already configured
            if (content.contains("spring-boot-starter-parent")) return; // parent handles annotation processing

            // If Lombok has no explicit <version> in <dependencies> the parent/BOM manages it.
            // annotationProcessorPaths does NOT inherit versions from <dependencyManagement>,
            // so injecting a guessed version risks a mismatch (e.g. TypeTag::UNKNOWN on JDK 22).
            // Skip injection — Maven resolves Lombok from the dependency classpath automatically.
            String lombokVersion = extractLombokVersionFromDeps(content);
            if (lombokVersion == null) return;

            int compilerIdx = content.indexOf("maven-compiler-plugin");
            if (compilerIdx == -1) return;

            // Find </configuration> that closes the compiler plugin's <configuration> block
            int configEnd = content.indexOf("</configuration>", compilerIdx);
            if (configEnd == -1) return;

            String processorBlock = """

                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>""" + lombokVersion + """
</version>
                        </path>
                    </annotationProcessorPaths>""";

            String newContent = content.substring(0, configEnd)
                    + processorBlock + "\n                "
                    + content.substring(configEnd);
            Files.writeString(pomPath, newContent);
            log.info("➕ [Dependency] Wired Lombok annotationProcessorPaths ({}) for {}",
                    lombokVersion, module.getServiceName());
        } catch (IOException e) {
            log.error("Failed to add Lombok annotationProcessorPaths for {}", module.getServiceName(), e);
        }
    }

    /**
     * Scans the pom content for an already-declared Lombok dependency and returns its
     * {@code <version>} value, or {@code null} if not found or still an unresolved placeholder.
     */
    private String extractLombokVersionFromDeps(String pomContent) {
        int lombokIdx = pomContent.indexOf("<artifactId>lombok</artifactId>");
        if (lombokIdx == -1) return null;
        int depEnd   = pomContent.indexOf("</dependency>", lombokIdx);
        int verStart = pomContent.indexOf("<version>", lombokIdx);
        if (verStart == -1 || (depEnd != -1 && verStart > depEnd)) return null;
        int verEnd = pomContent.indexOf("</version>", verStart);
        if (verEnd == -1) return null;
        String ver = pomContent.substring(verStart + "<version>".length(), verEnd).trim();
        return ver.startsWith("${") ? null : ver; // ignore unresolved placeholders
    }

    private void injectDependency(FractalModule module, Path serviceRoot, String checkString, String rawXml) {
        try {
            Path pomPath = serviceRoot.resolve("pom.xml");
            if (!Files.exists(pomPath)) return;

            String content = Files.readString(pomPath);
            // Prevent duplicate entries
            if (content.contains(checkString)) return;

            // Locate the end of the dependencies block
            int lastIndex = content.lastIndexOf("</dependencies>");
            if (lastIndex == -1) return;

            // Prepare the clean insertion point
            String start = content.substring(0, lastIndex).stripTrailing();
            String end = content.substring(lastIndex);

            // Insert the new dependency block
            String formattedBlock = "\n\n        " + rawXml;
            String newContent = start + formattedBlock + "\n    " + end;

            Files.writeString(pomPath, newContent);
            log.info("➕ [Dependency] Provisioned {} for {}", checkString, module.getServiceName());

        } catch (IOException e) {
            log.error("Failed to update pom.xml for " + module.getServiceName(), e);
        }
    }
}