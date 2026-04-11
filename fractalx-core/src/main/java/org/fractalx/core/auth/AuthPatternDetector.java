package org.fractalx.core.auth;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Scans the monolith source tree to detect whether it has a self-contained JWT auth pattern:
 * <ul>
 *   <li>A {@code @RestController} mapped to {@code /api/auth} (login + register endpoints)</li>
 *   <li>An entity implementing {@code UserDetails} (JPA user store)</li>
 *   <li>A {@code UserDetailsService} implementation</li>
 *   <li>{@code jwt.secret} and {@code jwt.expiration-ms} properties</li>
 * </ul>
 *
 * <p>When detected, {@link AuthServiceGenerator} is called to produce a standalone
 * {@code auth-service} that closes the auth gap left by decomposition.
 */
public class AuthPatternDetector {

    private static final Logger log = LoggerFactory.getLogger(AuthPatternDetector.class);

    private final Path projectRoot;

    public AuthPatternDetector(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * Runs detection and returns an {@link AuthPattern}. Never throws — returns
     * {@link AuthPattern#none()} on any error so the pipeline can continue safely.
     */
    public AuthPattern detect() {
        try {
            return doDetect();
        } catch (Exception e) {
            log.debug("AuthPatternDetector failed — treating as no-auth-pattern: {}", e.getMessage());
            return AuthPattern.none();
        }
    }

    private AuthPattern doDetect() throws IOException {
        // ── 1. Load JWT properties ──────────────────────────────────────────
        Map<String, String> props = loadProperties();
        String jwtSecret    = props.getOrDefault("jwt.secret",
                             props.get("jwt-secret"));
        String expStr       = props.getOrDefault("jwt.expiration-ms",
                             props.get("jwt.expiration"));
        long expirationMs   = 86_400_000L;
        if (expStr != null) {
            try { expirationMs = Long.parseLong(expStr.trim()); } catch (NumberFormatException ignored) {}
        }

        // ── 2. Walk source files ────────────────────────────────────────────
        Path srcMain = projectRoot.resolve("src/main/java");
        if (!Files.isDirectory(srcMain)) return AuthPattern.none();

        AtomicReference<String> authPkg        = new AtomicReference<>();
        AtomicReference<String> userDetailsPkg = new AtomicReference<>();
        boolean[] hasUserDetailsService = {false};

        JavaParser parser = new JavaParser();

        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> scan(p, parser, authPkg, userDetailsPkg, hasUserDetailsService));
        }

        boolean detected = jwtSecret != null
                && authPkg.get() != null
                && userDetailsPkg.get() != null;

        if (detected) {
            log.info("Auth pattern detected — authPkg={} userPkg={}", authPkg.get(), userDetailsPkg.get());
        } else {
            log.debug("No auth pattern detected (jwtSecret={} authPkg={} userPkg={})",
                    jwtSecret != null, authPkg.get(), userDetailsPkg.get());
        }

        return new AuthPattern(detected, jwtSecret, expirationMs, userDetailsPkg.get(), authPkg.get());
    }

    private void scan(Path javaFile, JavaParser parser,
                      AtomicReference<String> authPkg,
                      AtomicReference<String> userDetailsPkg,
                      boolean[] hasUDS) {
        try {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) return;

            String pkg = cu.getPackageDeclaration()
                           .map(pd -> pd.getNameAsString())
                           .orElse("");

            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {

                // ── Detect auth controller ──────────────────────────────────
                if (authPkg.get() == null && hasAnnotation(cls, "RestController")) {
                    boolean mappedToAuth = cls.getAnnotations().stream()
                            .anyMatch(a -> isRequestMappingToAuth(a))
                            || cls.getMethods().stream()
                               .anyMatch(m -> m.getAnnotations().stream()
                                              .anyMatch(a -> isMappingToAuthLogin(a)));
                    if (mappedToAuth) {
                        authPkg.set(pkg);
                        log.debug("Auth controller found in package {}", pkg);
                    }
                }

                // ── Detect UserDetails entity ───────────────────────────────
                if (userDetailsPkg.get() == null) {
                    boolean implementsUD = cls.getImplementedTypes().stream()
                            .anyMatch(t -> t.getNameAsString().equals("UserDetails"));
                    if (implementsUD && hasAnnotation(cls, "Entity")) {
                        userDetailsPkg.set(pkg);
                        log.debug("UserDetails entity found in package {}", pkg);
                    }
                }

                // ── Detect UserDetailsService impl ──────────────────────────
                if (!hasUDS[0]) {
                    boolean implementsUDS = cls.getImplementedTypes().stream()
                            .anyMatch(t -> t.getNameAsString().equals("UserDetailsService"));
                    if (implementsUDS) hasUDS[0] = true;
                }
            }
        } catch (Exception e) {
            log.trace("Could not parse {} for auth pattern: {}", javaFile.getFileName(), e.getMessage());
        }
    }

    private boolean hasAnnotation(ClassOrInterfaceDeclaration cls, String name) {
        return cls.getAnnotations().stream()
                  .anyMatch(a -> a.getNameAsString().equals(name));
    }

    private boolean isRequestMappingToAuth(AnnotationExpr ann) {
        String name = ann.getNameAsString();
        if (!name.equals("RequestMapping")) return false;
        String str = ann.toString();
        return str.contains("/api/auth") || str.contains("\"/auth\"");
    }

    private boolean isMappingToAuthLogin(AnnotationExpr ann) {
        String name = ann.getNameAsString();
        if (!name.equals("PostMapping") && !name.equals("GetMapping")
                && !name.equals("RequestMapping")) return false;
        String str = ann.toString();
        return str.contains("/api/auth") || str.contains("/auth/login") || str.contains("/auth/register");
    }

    // ── Property loading ────────────────────────────────────────────────────

    private Map<String, String> loadProperties() {
        Map<String, String> result = new LinkedHashMap<>();

        // application.properties (flat key=value)
        Path propsFile = projectRoot.resolve("src/main/resources/application.properties");
        if (Files.exists(propsFile)) {
            try {
                for (String line : Files.readAllLines(propsFile)) {
                    line = line.strip();
                    if (line.startsWith("#") || !line.contains("=")) continue;
                    int eq = line.indexOf('=');
                    result.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            } catch (IOException e) {
                log.debug("Could not read application.properties: {}", e.getMessage());
            }
        }

        // application.yml — flatten nested keys
        for (String name : new String[]{"application.yml", "application.yaml"}) {
            Path yml = projectRoot.resolve("src/main/resources/" + name);
            if (Files.exists(yml)) {
                try (InputStream is = Files.newInputStream(yml)) {
                    Object loaded = new Yaml().load(is);
                    if (loaded instanceof Map<?, ?> map) {
                        flattenMap(castMap(map), "", result);
                    }
                } catch (Exception e) {
                    log.debug("Could not parse {}: {}", name, e.getMessage());
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static void flattenMap(Map<String, Object> map, String prefix,
                                    Map<String, String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> nested) {
                flattenMap(castMap(nested), key, out);
            } else if (e.getValue() != null) {
                out.put(key, e.getValue().toString());
            }
        }
    }
}
