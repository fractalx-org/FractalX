package org.fractalx.core.generator.transformation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Copies monolith-local shared classes (utilities, middleware, common DTOs, etc.) that are
 * imported by module code but live outside any {@code @DecomposableModule} package.
 *
 * <p>Algorithm (BFS transitive closure):
 * <ol>
 *   <li>Seed the queue from every import in the already-copied module files.</li>
 *   <li>For each import FQN: skip if it is an external library (no source file in
 *       {@code sourceRoot}) or belongs to another module's package (handled by NetScope).</li>
 *   <li>Copy the file, then enqueue its own imports for the same check.</li>
 * </ol>
 *
 * <p>This step runs after {@link CodeCopier} and before {@link CodeTransformer}, so copied
 * shared files are subject to the same AST transformations as module files.
 */
public class SharedCodeCopier implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(SharedCodeCopier.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        Set<String> modulePkgPrefixes = context.getAllModules().stream()
                .map(FractalModule::getPackageName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        // Seed: collect shared imports from all already-copied .java files
        try (Stream<Path> paths = Files.walk(context.getSrcMainJava())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> enqueueSharedImports(
                            p, modulePkgPrefixes, context.getSourceRoot(), queue, visited));
        }

        // BFS: copy each shared class, then enqueue its own imports
        while (!queue.isEmpty()) {
            String fqn = queue.poll();
            Path src = context.getSourceRoot().resolve(fqn.replace('.', '/') + ".java");
            if (!Files.exists(src)) continue;

            Path dst = context.getSrcMainJava().resolve(fqn.replace('.', '/') + ".java");
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied shared class {} → {}", fqn, context.getModule().getServiceName());

            enqueueSharedImports(src, modulePkgPrefixes, context.getSourceRoot(), queue, visited);
        }
    }

    private void enqueueSharedImports(Path javaFile, Set<String> modulePkgs,
                                      Path sourceRoot, Queue<String> queue, Set<String> visited) {
        try {
            CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
            if (cu == null) return;
            for (ImportDeclaration imp : cu.getImports()) {
                if (imp.isStatic() || imp.isAsterisk()) continue;
                String fqn = imp.getNameAsString();
                if (!visited.add(fqn)) continue;

                // Must exist as source in the monolith — otherwise it is an external library
                if (!Files.exists(sourceRoot.resolve(fqn.replace('.', '/') + ".java"))) continue;

                // Must NOT be owned by another @DecomposableModule (those become NetScope clients)
                boolean isCrossModule = modulePkgs.stream()
                        .anyMatch(pkg -> fqn.startsWith(pkg + ".") || fqn.equals(pkg));
                if (isCrossModule) continue;

                queue.add(fqn);
            }
        } catch (Exception e) {
            log.debug("Could not parse {} for shared import detection", javaFile);
        }
    }
}
