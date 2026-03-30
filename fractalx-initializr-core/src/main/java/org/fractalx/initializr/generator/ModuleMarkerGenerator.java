package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates one {@code @DecomposableModule} marker class per service.
 * This is the entry-point class that {@code ModuleAnalyzer} scans during decomposition.
 *
 * <p>Example output for {@code order-service}:
 * <pre>
 * {@literal @}DecomposableModule(
 *     serviceName = "order-service",
 *     port = 8081,
 *     dependencies = {PaymentService.class, InventoryService.class},
 *     ownedSchemas = {"order_db"},
 *     independentDeployment = true
 * )
 * public class OrderModule { }
 * </pre>
 */
public class ModuleMarkerGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "@DecomposableModule markers"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();
        List<ServiceSpec> services = spec.getServices();

        for (ServiceSpec svc : services) {
            String basePkg   = spec.resolvedPackage();
            String svcPkg    = basePkg + "." + svc.javaPackage();
            String className = svc.classPrefix() + "Module";

            // Build dependency class array
            String depsArray = buildDepsArray(svc, services, basePkg);

            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(svcPkg).append(";\n\n");
            sb.append("import org.fractalx.annotations.DecomposableModule;\n");

            // Import dependency module classes
            for (String dep : svc.getDependencies()) {
                ServiceSpec depSvc = services.stream()
                        .filter(s -> s.getName().equals(dep))
                        .findFirst().orElse(null);
                if (depSvc != null) {
                    sb.append("import ").append(basePkg).append(".")
                      .append(depSvc.javaPackage()).append(".")
                      .append(depSvc.classPrefix()).append("Module;\n");
                }
            }
            sb.append("\n");

            sb.append("@DecomposableModule(\n");
            sb.append("    serviceName = \"").append(svc.getName()).append("\",\n");
            sb.append("    port = ").append(svc.getPort()).append(",\n");
            if (!depsArray.isEmpty()) {
                sb.append("    dependencies = {").append(depsArray).append("},\n");
            }
            sb.append("    ownedSchemas = {\"").append(svc.resolvedSchema()).append("\"},\n");
            sb.append("    independentDeployment = ").append(svc.isIndependentDeployment()).append("\n");
            sb.append(")\n");
            sb.append("public class ").append(className).append(" {\n");
            sb.append("    // Marker class — FractalX uses this annotation to identify the service boundary.\n");
            sb.append("    // Do not add business logic here.\n");
            sb.append("}\n");

            Path file = ctx.serviceSourceDir(svc).resolve(className + ".java");
            write(file, sb.toString());
        }
    }

    private String buildDepsArray(ServiceSpec svc, List<ServiceSpec> all, String basePkg) {
        return svc.getDependencies().stream()
                .map(dep -> all.stream().filter(s -> s.getName().equals(dep)).findFirst()
                        .map(d -> d.classPrefix() + "Module.class")
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));
    }
}
