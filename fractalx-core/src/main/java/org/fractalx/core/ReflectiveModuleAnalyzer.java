package org.fractalx.core;

import com.github.javaparser.JavaParser;
import org.fractalx.annotations.DecomposableModule;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyses {@code target/classes/} via a {@link URLClassLoader} instead of parsing source files.
 *
 * <p>Advantages over {@link ModuleAnalyzer}:
 * <ul>
 *   <li>Lombok-generated constructors (e.g. from {@code @AllArgsConstructor}) are fully expanded
 *       in bytecode — no suffix heuristic needed for dependency detection.</li>
 *   <li>{@code @DecomposableModule} attribute values are read as actual Java objects, not
 *       parsed strings — no risk of misquoting or annotation-expression edge-cases.</li>
 *   <li>{@code dependencies = {PaymentService.class}} yields real {@code Class} objects,
 *       so simple names are obtained via {@code Class::getSimpleName} with zero guesswork.</li>
 * </ul>
 *
 * <p>{@code detectedImports} (used by {@code PomGenerator} for Maven dep pruning) are still
 * collected from source files via JavaParser because import statements are stripped from bytecode.
 * Source files exist alongside {@code target/} so this remains reliable.
 *
 * <p>The caller (DecomposeMojo) should fall back to {@link ModuleAnalyzer} when
 * {@code target/classes/} is absent.
 */
public class ReflectiveModuleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveModuleAnalyzer.class);
    private final JavaParser javaParser = new JavaParser();

    /** Set during {@link #analyzeProject} so helper methods can load package-sibling classes. */
    private URLClassLoader moduleClassLoader;

    /**
     * Scans {@code classesDir} (e.g. {@code target/classes/}) for classes annotated with
     * {@code @DecomposableModule} and returns a {@link FractalModule} for each one found.
     *
     * @param classesDir      compiled output directory (must already exist)
     * @param sourceRoot      source root ({@code src/main/java}) used only for import collection;
     *                        may point to a non-existent path — imports will just be empty
     * @param parentClassLoader the plugin's own classloader; must already have
     *                          {@code fractalx-annotations} loaded so that
     *                          {@code clazz.getAnnotation(DecomposableModule.class)} resolves
     */
    public List<FractalModule> analyzeProject(Path classesDir, Path sourceRoot,
                                               ClassLoader parentClassLoader) throws IOException {
        moduleClassLoader = new URLClassLoader(
                new URL[]{ classesDir.toUri().toURL() },
                parentClassLoader
        );

        List<FractalModule> modules = new ArrayList<>();

        try (var stream = Files.walk(classesDir)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                  .filter(p -> !p.getFileName().toString().contains("$")) // skip inner/anon
                  .forEach(classFile -> {
                      String fqn = toFqn(classesDir, classFile);
                      try {
                          // initialize=false: skips static init; field types resolved lazily
                          Class<?> clazz = Class.forName(fqn, false, moduleClassLoader);
                          DecomposableModule ann = clazz.getAnnotation(DecomposableModule.class);
                          if (ann != null) {
                              modules.add(buildModule(clazz, ann, classesDir, sourceRoot));
                              log.info("Found decomposable module: {}  ({})",
                                      ann.serviceName(), fqn);
                          }
                      } catch (Throwable t) {
                          // NoClassDefFoundError / ClassNotFoundException when transitive
                          // dependencies (Spring, JPA, …) are not on the classpath — safe to skip
                          log.debug("Skipping {}: {}", fqn, t.getMessage());
                      }
                  });
        }

        // Validate: no two modules may share the same package subtree
        modules.stream()
               .collect(Collectors.groupingBy(FractalModule::getPackageName, Collectors.counting()))
               .forEach((pkg, count) -> {
                   if (count > 1) {
                       throw new IllegalStateException(
                           count + " @DecomposableModule classes share package '" + pkg + "'. "
                           + "Each module must have its own distinct package subtree. "
                           + "Move them into separate sub-packages "
                           + "(e.g., com.example.order, com.example.payment).");
                   }
               });

        return modules;
    }

    // ── Module construction ───────────────────────────────────────────────────

    private FractalModule buildModule(Class<?> clazz, DecomposableModule ann,
                                       Path classesDir, Path sourceRoot) throws IOException {
        String fqn = clazz.getName();
        String pkg = clazz.getPackageName();

        FractalModule.Builder b = FractalModule.builder()
                .className(fqn)
                .packageName(pkg)
                .serviceName(ann.serviceName())
                .independentDeployment(ann.independentDeployment());

        if (ann.port() > 0) b.port(ann.port());
        if (ann.ownedSchemas().length > 0) b.ownedSchemas(Arrays.asList(ann.ownedSchemas()));

        // Prefer explicit annotation value — most reliable
        List<String> deps;
        if (ann.dependencies().length > 0) {
            deps = Arrays.stream(ann.dependencies())
                         .map(Class::getSimpleName)
                         .toList();
            log.info("Explicit dependencies for {}: {}", ann.serviceName(), deps);
        } else {
            deps = inferDepsFromConstructors(clazz, pkg, classesDir);
            if (!deps.isEmpty()) {
                log.info("Constructor-inferred dependencies for {}: {}", ann.serviceName(), deps);
            }
        }
        b.dependencies(deps);

        // Import collection still uses source files — imports are absent from bytecode
        b.detectedImports(collectImportsForPackage(pkg, sourceRoot));

        return b.build();
    }

    // ── Dependency inference ─────────────────────────────────────────────────

    /**
     * Infers cross-module dependencies for the given {@code @DecomposableModule} class.
     *
     * <p>Strategy (in priority order):
     * <ol>
     *   <li><b>Largest constructor</b> — works when the module class itself has injected fields
     *       (e.g. Lombok {@code @AllArgsConstructor} on the module class).  Returns immediately
     *       if any constructor parameters are found.</li>
     *   <li><b>Package-wide field scan</b> — used when the module class is a bare marker
     *       ({@code @Configuration @DecomposableModule} with no fields/constructor params).
     *       Loads every compiled class in the same package and inspects their declared fields;
     *       any field whose type is not a local or well-known type is treated as a cross-module dep.</li>
     * </ol>
     */
    private List<String> inferDepsFromConstructors(Class<?> clazz,
                                                    String modulePkg, Path classesDir) {
        Set<String> localTypes = collectLocalTypeNames(modulePkg, classesDir);
        Set<String> deps = new LinkedHashSet<>();

        // ── Strategy 1: largest constructor on the @DecomposableModule class ────────────────────
        Constructor<?> largest = Arrays.stream(clazz.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElse(null);

        if (largest != null && largest.getParameterCount() > 0) {
            for (Parameter p : largest.getParameters()) {
                try {
                    String simple = p.getType().getSimpleName();
                    if (!isWellKnownType(simple) && !localTypes.contains(simple)) {
                        deps.add(simple);
                    }
                } catch (Throwable t) {
                    log.debug("Could not resolve constructor param in {}: {}",
                            clazz.getName(), t.getMessage());
                }
            }
            if (!deps.isEmpty()) return new ArrayList<>(deps);
        }

        // ── Strategy 2: scan all field types in every class in the module package ─────────────
        // Handles the common pattern: @DecomposableModule is a bare marker class; actual
        // @Autowired cross-module fields live in @Service / @Component siblings.
        log.debug("Module class {} has no constructor params — scanning package fields for deps",
                clazz.getSimpleName());
        scanPackageFieldsForDeps(modulePkg, classesDir, localTypes, deps);
        return new ArrayList<>(deps);
    }

    /**
     * Walks every compiled class in {@code modulePkg}'s directory, loads it, and adds any
     * declared field type that is neither a local module type nor a well-known library type
     * to {@code deps}.
     */
    private void scanPackageFieldsForDeps(String modulePkg, Path classesDir,
                                           Set<String> localTypes, Set<String> deps) {
        Path pkgDir = classesDir.resolve(modulePkg.replace('.', '/'));
        if (!Files.isDirectory(pkgDir)) return;

        try (var stream = Files.walk(pkgDir)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                  .filter(p -> !p.getFileName().toString().contains("$"))
                  .forEach(classFile -> {
                      String fqn = toFqn(classesDir, classFile);
                      try {
                          Class<?> c = Class.forName(fqn, false, moduleClassLoader);
                          for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                              try {
                                  String simple = f.getType().getSimpleName();
                                  // Apply suffix heuristic to avoid treating entity/model fields
                                  // (e.g. Customer inside Order) as cross-module service deps.
                                  if ((simple.endsWith("Service") || simple.endsWith("Client"))
                                          && !localTypes.contains(simple)) {
                                      deps.add(simple);
                                      log.debug("Field dep '{}' found in {}", simple, fqn);
                                  }
                              } catch (Throwable t) {
                                  log.debug("Could not resolve field type in {}: {}", fqn, t.getMessage());
                              }
                          }
                      } catch (Throwable t) {
                          log.debug("Skipping class {} during field scan: {}", fqn, t.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.debug("Could not walk package dir {}: {}", pkgDir, e.getMessage());
        }
    }

    /** Returns the simple names of all top-level classes in {@code pkg}'s directory. */
    private Set<String> collectLocalTypeNames(String pkg, Path classesDir) {
        Path pkgDir = classesDir.resolve(pkg.replace('.', '/'));
        if (!Files.isDirectory(pkgDir)) return Set.of();
        Set<String> names = new HashSet<>();
        try (var stream = Files.walk(pkgDir)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                  .filter(p -> !p.getFileName().toString().contains("$"))
                  .forEach(p -> names.add(p.getFileName().toString().replace(".class", "")));
        } catch (IOException e) {
            log.debug("Could not walk package dir {}: {}", pkgDir, e.getMessage());
        }
        return names;
    }

    // ── Import collection (source-based, same logic as ModuleAnalyzer) ───────

    private Set<String> collectImportsForPackage(String basePackage, Path sourceRoot) {
        Set<String> imports = new LinkedHashSet<>();
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) return imports;
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> {
                      try {
                          javaParser.parse(p).getResult().ifPresent(cu -> {
                              String pkg = cu.getPackageDeclaration()
                                      .map(pd -> pd.getNameAsString()).orElse("");
                              if (pkg.equals(basePackage) || pkg.startsWith(basePackage + ".")) {
                                  cu.getImports().forEach(imp ->
                                          imports.add(imp.getNameAsString()));
                              }
                          });
                      } catch (IOException e) {
                          log.debug("Could not parse {} for imports: {}", p, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.debug("Could not walk source root {}: {}", sourceRoot, e.getMessage());
        }
        return imports;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Converts a {@code .class} file path relative to classesDir into a fully-qualified name. */
    private static String toFqn(Path classesDir, Path classFile) {
        return classesDir.relativize(classFile)
                .toString()
                .replace('/', '.')
                .replace('\\', '.')
                .replace(".class", "");
    }

    /**
     * Returns {@code true} for JDK primitives, common JDK types, and well-known framework types
     * that should never be treated as cross-module dependencies.
     * Identical to {@link ModuleAnalyzer#isWellKnownType} — kept in sync manually.
     */
    private static boolean isWellKnownType(String typeName) {
        if (typeName == null || typeName.isEmpty()) return true;
        String raw = typeName.contains("<") ? typeName.substring(0, typeName.indexOf('<')) : typeName;
        return switch (raw) {
            case "String", "Integer", "Long", "Double", "Float", "Boolean",
                 "Byte", "Short", "Character", "Object", "Number",
                 "int", "long", "double", "float", "boolean", "byte", "short", "char", "void",
                 "List", "Map", "Set", "Collection", "Iterable", "Optional",
                 "Stream", "Flux", "Mono",
                 "BigDecimal", "BigInteger", "UUID",
                 "LocalDate", "LocalDateTime", "LocalTime", "Instant", "Duration",
                 "Path", "File", "InputStream", "OutputStream",
                 "Logger", "ObjectMapper", "RestTemplate", "WebClient",
                 "ApplicationEventPublisher", "Environment", "MessageSource" -> true;
            default -> raw.startsWith("java.") || raw.startsWith("jakarta.")
                    || raw.startsWith("org.springframework.")
                    || raw.startsWith("io.") || raw.startsWith("com.fasterxml.");
        };
    }
}
