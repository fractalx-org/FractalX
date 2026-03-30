package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.ProjectSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates {@code .github/workflows/ci.yml} — a GitHub Actions pipeline that
 * builds, tests, and (optionally) packages the monolith.
 */
public class GitHubActionsGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "GitHub Actions CI"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        if (!ctx.spec().getInfrastructure().isGithubActions()) return;

        ProjectSpec spec = ctx.spec();

        StringBuilder sb = new StringBuilder();
        sb.append("name: CI\n\n");
        sb.append("on:\n");
        sb.append("  push:\n");
        sb.append("    branches: [ main, develop ]\n");
        sb.append("  pull_request:\n");
        sb.append("    branches: [ main ]\n\n");

        sb.append("jobs:\n");
        sb.append("  build:\n");
        sb.append("    runs-on: ubuntu-latest\n\n");
        sb.append("    services:\n");

        // Spin up DB services in CI if needed
        boolean hasPostgres = spec.getServices().stream()
                .anyMatch(s -> "postgresql".equalsIgnoreCase(s.getDatabase()));
        boolean hasMysql = spec.getServices().stream()
                .anyMatch(s -> "mysql".equalsIgnoreCase(s.getDatabase()));
        boolean hasMongo = spec.getServices().stream()
                .anyMatch(s -> "mongodb".equalsIgnoreCase(s.getDatabase()));

        if (hasPostgres) {
            sb.append("      postgres:\n");
            sb.append("        image: postgres:16-alpine\n");
            sb.append("        env:\n");
            sb.append("          POSTGRES_USER: fractalx\n");
            sb.append("          POSTGRES_PASSWORD: fractalx\n");
            sb.append("          POSTGRES_DB: fractalx_test\n");
            sb.append("        ports:\n");
            sb.append("          - 5432:5432\n");
            sb.append("        options: >-\n");
            sb.append("          --health-cmd pg_isready\n");
            sb.append("          --health-interval 10s\n");
            sb.append("          --health-timeout 5s\n");
            sb.append("          --health-retries 5\n\n");
        }
        if (hasMysql) {
            sb.append("      mysql:\n");
            sb.append("        image: mysql:8.3\n");
            sb.append("        env:\n");
            sb.append("          MYSQL_ROOT_PASSWORD: fractalx\n");
            sb.append("          MYSQL_DATABASE: fractalx_test\n");
            sb.append("        ports:\n");
            sb.append("          - 3306:3306\n");
            sb.append("        options: --health-cmd=\"mysqladmin ping\" --health-interval=10s --health-retries=5\n\n");
        }
        if (hasMongo) {
            sb.append("      mongodb:\n");
            sb.append("        image: mongo:7\n");
            sb.append("        ports:\n");
            sb.append("          - 27017:27017\n\n");
        }

        sb.append("    steps:\n");
        sb.append("      - name: Checkout\n");
        sb.append("        uses: actions/checkout@v4\n\n");

        sb.append("      - name: Set up JDK ").append(spec.getJavaVersion()).append("\n");
        sb.append("        uses: actions/setup-java@v4\n");
        sb.append("        with:\n");
        sb.append("          java-version: '").append(spec.getJavaVersion()).append("'\n");
        sb.append("          distribution: 'temurin'\n");
        sb.append("          cache: maven\n\n");

        sb.append("      - name: Build and test\n");
        sb.append("        run: mvn -B verify --no-transfer-progress\n\n");

        sb.append("      - name: Upload test results\n");
        sb.append("        if: always()\n");
        sb.append("        uses: actions/upload-artifact@v4\n");
        sb.append("        with:\n");
        sb.append("          name: test-results\n");
        sb.append("          path: target/surefire-reports/\n");

        Path workflowDir = ctx.outputRoot().resolve(".github/workflows");
        Files.createDirectories(workflowDir);
        write(workflowDir.resolve("ci.yml"), sb.toString());
    }
}
