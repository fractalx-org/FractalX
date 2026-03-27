package org.fractalx.core.generator.service;

import org.fractalx.core.FractalxVersion;
import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.observability.ObservabilityInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates pom.xml for a microservice.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Read every {@code <dependency>} from the source monolith's {@code pom.xml}.</li>
 *   <li>Remove dependencies whose artifact is clearly unused in this service's source
 *       files (based on imports detected by {@link org.fractalx.core.ModuleAnalyzer}).</li>
 *   <li>Always add the FractalX-required deps (NetScope, runtime, actuator,
 *       resilience4j) that the monolith itself never declares.</li>
 * </ol>
 */
public class PomGenerator implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(PomGenerator.class);

    private static final String FRACTALX_RUNTIME_VERSION = FractalxVersion.get();
    private static final String NETSCOPE_VERSION         = "1.0.1";

    /**
     * ArtifactId fragments of deps we can safely drop when the service's source
     * files contain no matching imports.  Everything NOT in this list is kept
     * by default — we only prune what we are confident about.
     */
    private static final List<PruneRule> PRUNE_RULES = List.of(
            new PruneRule("data-jpa",   "jakarta.persistence", "org.springframework.data.jpa", "org.springframework.data.repository"),
            new PruneRule("hibernate",  "jakarta.persistence", "org.hibernate"),
            new PruneRule("flyway",     "jakarta.persistence", "org.flywaydb", "org.springframework.data.jpa"),
            new PruneRule("mysql",      "com.mysql"),
            new PruneRule("postgresql", "org.postgresql"),
            new PruneRule("mongodb",    "org.springframework.data.mongodb"),
            new PruneRule("redis",      "org.springframework.data.redis"),
            new PruneRule("kafka",      "org.springframework.kafka"),
            new PruneRule("amqp",       "org.springframework.amqp"),
            new PruneRule("rabbit",     "org.springframework.amqp"),
            new PruneRule("security",   "org.springframework.security"),
            new PruneRule("validation", "jakarta.validation", "org.springframework.validation"),
            new PruneRule("lombok",     "lombok"),
            new PruneRule("h2",         "jakarta.persistence", "org.springframework.data.jpa")
    );

    /**
     * ArtifactId fragments that FractalX always injects — skip from monolith copy to avoid
     * duplicates or unresolvable version placeholders.
     *
     * <p>{@code fractalx-annotations} and {@code fractalx-core} are build-time generator
     * dependencies of the monolith; generated services contain none of those annotations and
     * need neither jar at compile or runtime.  Copying them verbatim from the monolith pom
     * would also carry the unresolved {@code ${fractalx.version}} placeholder into the
     * generated service's pom, causing Maven to refuse to build the service.
     */
    private static final Set<String> FRACTALX_MANAGED = Set.of(
            "netscope-server", "netscope-client", "fractalx-runtime",
            "fractalx-annotations", "fractalx-core",
            "spring-boot-starter-web", "spring-boot-starter-actuator",
            "spring-boot-starter-aop", "resilience4j-spring-boot3",
            "spring-cloud-context"
    );

    private final ObservabilityInjector observabilityInjector;

    public PomGenerator(ObservabilityInjector observabilityInjector) {
        this.observabilityInjector = observabilityInjector;
    }

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        FractalxConfig cfg = context.getFractalxConfig();
        log.debug("Generating pom.xml for {}", module.getServiceName());
        Files.writeString(context.getServiceRoot().resolve("pom.xml"),
                buildPomContent(module, context.getServiceRoot(), cfg));
    }

    // ── POM assembly ─────────────────────────────────────────────────────────

    private String buildPomContent(FractalModule module, Path serviceRoot, FractalxConfig cfg) {
        String filteredDeps = buildFilteredMonolithDeps(module, serviceRoot);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <packaging>jar</packaging>

                    <name>%s</name>
                    <description>Generated microservice by FractalX</description>

                    <properties>
                        <java.version>17</java.version>
                        <spring-boot.version>%s</spring-boot.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>${spring-boot.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.cloud</groupId>
                                <artifactId>spring-cloud-dependencies</artifactId>
                                <version>%s</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <!-- ── FractalX required ────────────────────────────── -->
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>netscope-server</artifactId>
                            <version>%s</version>
                        </dependency>
                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>netscope-client</artifactId>
                            <version>%s</version>
                        </dependency>
                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>fractalx-runtime</artifactId>
                            <version>%s</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-actuator</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>io.github.resilience4j</groupId>
                            <artifactId>resilience4j-spring-boot3</artifactId>
                            <version>2.1.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-aop</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-context</artifactId>
                        </dependency>

                        <!-- ── From source monolith pom (unused deps removed) ── -->
                %s
                        <!-- ── Observability (OTel tracing) ─────────────────── -->
                %s
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                                <configuration>
                                    <parameters>true</parameters>
                                </configuration>
                            </plugin>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>${spring-boot.version}</version>
                                <executions>
                                    <execution>
                                        <goals>
                                            <goal>repackage</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(
                cfg.effectiveBasePackage(),
                module.getServiceName(),
                module.getServiceName(),
                cfg.springBootVersion(),
                cfg.springCloudVersion(),
                NETSCOPE_VERSION,
                NETSCOPE_VERSION,
                FRACTALX_RUNTIME_VERSION,
                filteredDeps,
                observabilityInjector.getDependencies()
        );
    }

    // ── Monolith dep reading + filtering ─────────────────────────────────────

    private String buildFilteredMonolithDeps(FractalModule module, Path serviceRoot) {
        // monolith root = fractalx-output/../  =  serviceRoot/../../
        Path monolithPom = serviceRoot.getParent().getParent().resolve("pom.xml");
        if (!Files.exists(monolithPom)) {
            log.debug("No monolith pom found at {} — skipping dep clone", monolithPom);
            return "";
        }

        // Read monolith properties once so we can resolve ${placeholder} version refs.
        Map<String, String> monolithProps = readMonolithProperties(monolithPom);

        List<Element> deps = readDependencyElements(monolithPom);
        if (deps.isEmpty()) return "";

        Set<String> imports = module.getDetectedImports();
        StringBuilder sb = new StringBuilder();
        int kept = 0, pruned = 0;

        for (Element dep : deps) {
            String artifactId = text(dep, "artifactId");

            // Skip deps FractalX always manages itself
            if (FRACTALX_MANAGED.contains(artifactId)) continue;

            if (isDependencyUsed(artifactId, imports)) {
                // Resolve ${...} version placeholders before copying into the generated pom.
                // If a placeholder cannot be resolved, skip the dep rather than emit an
                // invalid version that would break the generated service's build.
                if (!resolveVersionPlaceholder(dep, artifactId, monolithProps)) {
                    pruned++;
                    continue;
                }
                sb.append(elementToString(dep));
                kept++;
            } else {
                log.debug("Pruned unused dep '{}' from {}", artifactId, module.getServiceName());
                pruned++;
            }
        }

        log.info("Dep clone for {}: {} kept, {} pruned", module.getServiceName(), kept, pruned);
        return sb.toString();
    }

    /**
     * Reads {@code <dependency>} elements from {@code <project><dependencies>}
     * (skipping those inside {@code <dependencyManagement>}).
     */
    private List<Element> readDependencyElements(Path pomPath) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(pomPath.toFile());
            doc.getDocumentElement().normalize();

            // Find the <dependencies> child of <project>, not <dependencyManagement>
            NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "dependencies".equals(child.getNodeName())) {
                    return collectDependencyElements((Element) child);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse monolith pom: {}", e.getMessage());
        }
        return List.of();
    }

    private List<Element> collectDependencyElements(Element dependenciesEl) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = dependenciesEl.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) nodes.item(i));
            }
        }
        return result;
    }

    // ── Usage check ───────────────────────────────────────────────────────────

    /**
     * Returns {@code false} only when a prune rule matches the artifactId AND
     * none of the rule's required import prefixes are detected.
     * Everything else is kept (safe default).
     */
    private static boolean isDependencyUsed(String artifactId, Set<String> imports) {
        String aid = artifactId.toLowerCase();
        for (PruneRule rule : PRUNE_RULES) {
            if (aid.contains(rule.artifactFragment())) {
                // Prune only if no matching import found
                return importsAny(imports, rule.requiredPrefixes());
            }
        }
        return true; // cannot determine — keep it
    }

    private static boolean importsAny(Set<String> imports, String... prefixes) {
        for (String imp : imports) {
            for (String prefix : prefixes) {
                if (imp.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    // ── Monolith property resolution ─────────────────────────────────────────

    /**
     * Reads the {@code <properties>} block from the monolith's {@code pom.xml} into a map
     * so that version placeholders like {@code ${fractalx.version}} can be resolved before
     * the dependency element is copied into a generated service pom.
     */
    private Map<String, String> readMonolithProperties(Path pomPath) {
        Map<String, String> props = new HashMap<>();
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(pomPath.toFile());
            doc.getDocumentElement().normalize();
            NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "properties".equals(child.getNodeName())) {
                    NodeList propNodes = child.getChildNodes();
                    for (int j = 0; j < propNodes.getLength(); j++) {
                        Node p = propNodes.item(j);
                        if (p.getNodeType() == Node.ELEMENT_NODE)
                            props.put(p.getNodeName(), p.getTextContent().trim());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Could not read <properties> from monolith pom: {}", e.getMessage());
        }
        return props;
    }

    /**
     * If the {@code <version>} of {@code dep} is a {@code ${key}} placeholder, replaces its
     * text content with the resolved value from {@code props}.
     *
     * @return {@code true} when the version is usable (literal, absent, or successfully
     *         resolved); {@code false} when the placeholder cannot be resolved — in that case
     *         the caller should skip this dep to avoid emitting an invalid generated pom.
     */
    private boolean resolveVersionPlaceholder(Element dep, String artifactId,
                                              Map<String, String> props) {
        NodeList versions = dep.getElementsByTagName("version");
        if (versions.getLength() == 0) return true;   // no <version> — inherited from BOM, fine

        Node vn = versions.item(0);
        String ver = vn.getTextContent().trim();
        if (!ver.startsWith("${") || !ver.endsWith("}")) return true;  // literal version

        String key = ver.substring(2, ver.length() - 1);
        String resolved = props.get(key);
        if (resolved != null) {
            vn.setTextContent(resolved);
            log.debug("Resolved version placeholder '{}' → '{}' for dep '{}'",
                    ver, resolved, artifactId);
            return true;
        }

        log.warn("Skipping dep '{}' from monolith — version placeholder '{}' is not defined "
                + "in monolith <properties>. Declare the property in the monolith pom or "
                + "add the dep manually to fractalx-config.yml.",
                artifactId, ver);
        return false;
    }

    // ── XML helpers ───────────────────────────────────────────────────────────

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        return nl.item(0).getTextContent().trim();
    }

    private static String elementToString(Element el) {
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(el), new StreamResult(sw));
            // Indent everything by 8 spaces to match generated POM style
            String raw = sw.toString().trim();
            return raw.lines()
                    .map(line -> "        " + line)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("") + "\n";
        } catch (Exception e) {
            return "";
        }
    }

    // ── Prune rule ────────────────────────────────────────────────────────────

    private record PruneRule(String artifactFragment, String... requiredPrefixes) {}
}
