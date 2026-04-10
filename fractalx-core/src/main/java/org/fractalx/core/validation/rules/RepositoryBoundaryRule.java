package org.fractalx.core.validation.rules;

import org.fractalx.core.datamanagement.RepositoryAnalyzer;
import org.fractalx.core.datamanagement.RepositoryAnalyzer.RepositoryReport;
import org.fractalx.core.datamanagement.RepositoryAnalyzer.Violation;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Blocks decomposition when a module directly injects a JPA repository that belongs
 * to a different module.
 *
 * <p>After decomposition, each service has its own datasource — it cannot access
 * another service's database schema directly. Code like:
 * <pre>
 *   // In order-service package:
 *   &#64;Autowired PaymentRepository paymentRepo;  // owned by payment-service
 * </pre>
 * will compile but throw {@code NoSuchBeanDefinitionException} at runtime because
 * {@code PaymentRepository} and its datasource live in a different JVM.
 *
 * <p>Fix: move the data access logic into the owning service and expose it via
 * a service method, or introduce a read-model/snapshot pattern.
 */
public class RepositoryBoundaryRule implements ValidationRule {

    @Override
    public String ruleId() { return "REPO_BOUNDARY"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) throws IOException {
        List<ValidationIssue> issues = new ArrayList<>();

        RepositoryReport report = new RepositoryAnalyzer()
                .analyze(ctx.sourceRoot(), ctx.modules());

        for (Violation v : report.violations) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR, ruleId(), v.usedInModule,
                    v.usedInClass + " injects " + v.repositoryName
                    + " which is owned by '" + v.ownerModule + "'",
                    "Move the data access logic into '"
                    + v.ownerModule + "' and expose it via a service method"));
        }
        return issues;
    }
}
