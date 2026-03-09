package org.fractalx.core.generator.service;

import org.fractalx.core.FractalxVersion;
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
import java.util.List;
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

    /** ArtifactId fragments that FractalX always injects — skip from monolith copy to avoid duplicates. */
    private static final Set<String> FRACTALX_MANAGED = Set.of(
            "netscope-server", "netscope-client", "fractalx-runtime",
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
        log.debug("Generating pom.xml for {}", module.getServiceName());
        Files.writeString(context.getServiceRoot().resolve("pom.xml"),
                buildPomContent(module, context.getServiceRoot()));
    }

    // ── POM assembly ─────────────────────────────────────────────────────────

    private String buildPomContent(FractalModule module, Path serviceRoot) {
        String filteredDeps = buildFilteredMonolithDeps(module, serviceRoot);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>org.fractalx.generated</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <packaging>jar</packaging>

                    <name>%s</name>
                    <description>Generated microservice by FractalX</description>

                    <properties>
                        <java.version>17</java.version>
                        <spring-boot.version>3.2.0</spring-boot.version>
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
                                <version>2023.0.0</version>
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
                module.getServiceName(),
                module.getServiceName(),
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
