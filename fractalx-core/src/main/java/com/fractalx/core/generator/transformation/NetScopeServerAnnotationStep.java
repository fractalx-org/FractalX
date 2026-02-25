package com.fractalx.core.generator.transformation;

import com.fractalx.core.generator.GenerationContext;
import com.fractalx.core.generator.ServiceFileGenerator;
import com.fractalx.core.generator.service.NetScopeClientGenerator;
import com.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds {@code @NetworkPublic} to every public non-static method of beans that
 * are consumed by other modules.
 *
 * <p>After source files are copied and transformed, this step walks the generated
 * service's {@code src/main/java} directory. For each {@code .java} file whose
 * class simple name matches a bean type exported to other modules, it uses
 * JavaParser to inject the {@code @NetworkPublic} annotation and rewrites the file.
 *
 * <p>The set of exposed beans is derived at generation time by inspecting the
 * {@link FractalModule#getDependencies()} of every other module: if a dependency
 * type maps (by naming convention) to the service being generated, the corresponding
 * bean needs the server-side annotation so NetScope-Server can expose it over gRPC.
 */
public class NetScopeServerAnnotationStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(NetScopeServerAnnotationStep.class);

    private static final String NETWORK_PUBLIC_IMPORT = "org.fractalx.netscope.server.annotation.NetworkPublic";

    @Override
    public void generate(GenerationContext context) throws IOException {
        Set<String> exposedBeans = findExposedBeans(context);

        if (exposedBeans.isEmpty()) {
            log.debug("No exposed beans for {} – skipping @NetworkPublic annotation",
                    context.getModule().getServiceName());
            return;
        }

        log.debug("Annotating beans with @NetworkPublic in {}: {}", context.getModule().getServiceName(), exposedBeans);

        Files.walk(context.getSrcMainJava())
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> exposedBeans.contains(p.getFileName().toString().replace(".java", "")))
                .forEach(path -> {
                    try {
                        annotateWithNetworkPublic(path);
                    } catch (IOException e) {
                        log.error("Failed to annotate {} with @NetworkPublic", path.getFileName(), e);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Exposed-bean detection
    // -------------------------------------------------------------------------

    /**
     * Collects the simple type names of beans in this module that are referenced
     * as dependencies by at least one other module.
     */
    private Set<String> findExposedBeans(GenerationContext context) {
        String thisService = context.getModule().getServiceName();
        Set<String> exposed = new HashSet<>();

        for (FractalModule other : context.getAllModules()) {
            if (thisService.equals(other.getServiceName())) {
                continue;
            }
            for (String dep : other.getDependencies()) {
                if (thisService.equals(NetScopeClientGenerator.beanTypeToServiceName(dep))) {
                    exposed.add(dep);
                }
            }
        }
        return exposed;
    }

    // -------------------------------------------------------------------------
    // AST annotation injection
    // -------------------------------------------------------------------------

    private void annotateWithNetworkPublic(java.nio.file.Path javaFile) throws IOException {
        CompilationUnit cu = new JavaParser().parse(javaFile).getResult()
                .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

        boolean modified = false;

        // Ensure the import is present
        boolean hasImport = cu.getImports().stream()
                .anyMatch(i -> NETWORK_PUBLIC_IMPORT.equals(i.getNameAsString()));
        if (!hasImport) {
            cu.addImport(NETWORK_PUBLIC_IMPORT);
        }

        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            for (MethodDeclaration method : classDecl.getMethods()) {
                if (method.isPublic()
                        && !method.isStatic()
                        && method.getAnnotationByName("NetworkPublic").isEmpty()
                        && method.getAnnotationByName("NetworkSecured").isEmpty()) {

                    method.addAnnotation("NetworkPublic");
                    modified = true;
                }
            }
        }

        if (modified) {
            Files.writeString(javaFile, cu.toString());
            log.info("Added @NetworkPublic to public methods in: {}", javaFile.getFileName());
        }
    }
}
