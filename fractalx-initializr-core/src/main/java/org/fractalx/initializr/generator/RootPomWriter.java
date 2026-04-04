package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.SecuritySpec;

import java.io.IOException;

/**
 * Generates the root {@code pom.xml} for the scaffolded monolith.
 * The pom pre-wires the fractalx-maven-plugin so that {@code mvn fractalx:decompose}
 * works on day one without any manual configuration.
 */
public class RootPomWriter implements InitializerFileGenerator {

    @Override
    public String label() { return "pom.xml"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();
        SecuritySpec sec = spec.getSecurity();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n");
        sb.append("         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");

        sb.append("    <parent>\n");
        sb.append("        <groupId>org.springframework.boot</groupId>\n");
        sb.append("        <artifactId>spring-boot-starter-parent</artifactId>\n");
        sb.append("        <version>").append(spec.getSpringBootVersion()).append("</version>\n");
        sb.append("        <relativePath/>\n");
        sb.append("    </parent>\n\n");

        sb.append("    <groupId>").append(spec.getGroupId()).append("</groupId>\n");
        sb.append("    <artifactId>").append(spec.getArtifactId()).append("</artifactId>\n");
        sb.append("    <version>").append(spec.getVersion()).append("</version>\n");
        sb.append("    <name>").append(spec.getArtifactId()).append("</name>\n");
        sb.append("    <description>").append(spec.getDescription()).append("</description>\n\n");

        sb.append("    <properties>\n");
        sb.append("        <java.version>").append(spec.getJavaVersion()).append("</java.version>\n");
        sb.append("        <fractalx.version>0.3.1</fractalx.version>\n");
        sb.append("    </properties>\n\n");

        sb.append("    <dependencies>\n");
        sb.append("        <!-- FractalX annotations -->\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.fractalx</groupId>\n");
        sb.append("            <artifactId>fractalx-annotations</artifactId>\n");
        sb.append("            <version>${fractalx.version}</version>\n");
        sb.append("        </dependency>\n\n");

        sb.append("        <!-- Spring Web -->\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-web</artifactId>\n");
        sb.append("        </dependency>\n\n");

        sb.append("        <!-- Spring Actuator -->\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-actuator</artifactId>\n");
        sb.append("        </dependency>\n\n");

        // Emit DB-specific deps for each service database type
        boolean hasJpa       = spec.getServices().stream().anyMatch(s -> s.usesJpa());
        boolean hasPostgres  = spec.getServices().stream().anyMatch(s -> "postgresql".equalsIgnoreCase(s.getDatabase()));
        boolean hasMysql     = spec.getServices().stream().anyMatch(s -> "mysql".equalsIgnoreCase(s.getDatabase()));
        boolean hasH2        = spec.getServices().stream().anyMatch(s -> "h2".equalsIgnoreCase(s.getDatabase()));
        boolean hasMongo     = spec.getServices().stream().anyMatch(s -> "mongodb".equalsIgnoreCase(s.getDatabase()));
        boolean hasRedis     = spec.getServices().stream().anyMatch(s -> "redis".equalsIgnoreCase(s.getDatabase()));
        boolean hasFlyway    = spec.getServices().stream().anyMatch(s -> s.usesFlyway());

        if (hasJpa) {
            sb.append("        <!-- JPA -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-data-jpa</artifactId>\n");
            sb.append("        </dependency>\n\n");
        }
        if (hasPostgres) {
            sb.append("        <!-- PostgreSQL driver -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.postgresql</groupId>\n");
            sb.append("            <artifactId>postgresql</artifactId>\n");
            sb.append("            <scope>runtime</scope>\n");
            sb.append("        </dependency>\n\n");
        }
        if (hasMysql) {
            sb.append("        <!-- MySQL driver -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>com.mysql</groupId>\n");
            sb.append("            <artifactId>mysql-connector-j</artifactId>\n");
            sb.append("            <scope>runtime</scope>\n");
            sb.append("        </dependency>\n\n");
        }
        if (hasH2) {
            sb.append("        <!-- H2 in-memory (dev) -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>com.h2database</groupId>\n");
            sb.append("            <artifactId>h2</artifactId>\n");
            sb.append("            <scope>runtime</scope>\n");
            sb.append("        </dependency>\n\n");
        }
        if (hasFlyway) {
            sb.append("        <!-- Flyway migrations -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.flywaydb</groupId>\n");
            sb.append("            <artifactId>flyway-core</artifactId>\n");
            sb.append("        </dependency>\n\n");
        }
        if (hasMongo) {
            sb.append("        <!-- MongoDB -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-data-mongodb</artifactId>\n");
            sb.append("        </dependency>\n\n");
        }
        if (hasRedis) {
            sb.append("        <!-- Redis -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-data-redis</artifactId>\n");
            sb.append("        </dependency>\n\n");
        }
        if (sec.isJwt()) {
            sb.append("        <!-- Spring Security + JWT -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-security</artifactId>\n");
            sb.append("        </dependency>\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>io.jsonwebtoken</groupId>\n");
            sb.append("            <artifactId>jjwt-api</artifactId>\n");
            sb.append("            <version>0.12.3</version>\n");
            sb.append("        </dependency>\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>io.jsonwebtoken</groupId>\n");
            sb.append("            <artifactId>jjwt-impl</artifactId>\n");
            sb.append("            <version>0.12.3</version>\n");
            sb.append("            <scope>runtime</scope>\n");
            sb.append("        </dependency>\n\n");
        }
        if (sec.isOAuth2()) {
            sb.append("        <!-- OAuth2 Resource Server -->\n");
            sb.append("        <dependency>\n");
            sb.append("            <groupId>org.springframework.boot</groupId>\n");
            sb.append("            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>\n");
            sb.append("        </dependency>\n\n");
        }

        sb.append("        <!-- Bean validation -->\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-validation</artifactId>\n");
        sb.append("        </dependency>\n\n");

        sb.append("        <!-- Test -->\n");
        sb.append("        <dependency>\n");
        sb.append("            <groupId>org.springframework.boot</groupId>\n");
        sb.append("            <artifactId>spring-boot-starter-test</artifactId>\n");
        sb.append("            <scope>test</scope>\n");
        sb.append("        </dependency>\n");
        sb.append("    </dependencies>\n\n");

        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.springframework.boot</groupId>\n");
        sb.append("                <artifactId>spring-boot-maven-plugin</artifactId>\n");
        sb.append("            </plugin>\n\n");
        sb.append("            <!-- FractalX decomposition plugin --\n");
        sb.append("                 Run: mvn fractalx:decompose  to extract microservices -->\n");
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.fractalx</groupId>\n");
        sb.append("                <artifactId>fractalx-maven-plugin</artifactId>\n");
        sb.append("                <version>${fractalx.version}</version>\n");
        sb.append("            </plugin>\n");
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");
        sb.append("</project>\n");

        write(ctx.outputRoot().resolve("pom.xml"), sb.toString());
    }
}
