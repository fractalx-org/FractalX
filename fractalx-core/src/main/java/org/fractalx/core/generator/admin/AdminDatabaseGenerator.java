package org.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the database persistence layer for the admin service.
 *
 * <p>The admin service supports two storage modes:
 * <ul>
 *   <li><b>Default (memory)</b> — in-memory stores; no datasource required</li>
 *   <li><b>DB mode</b> — Spring Data JPA backed; activate via {@code -Dspring.profiles.active=db}</li>
 * </ul>
 *
 * <p>Generated files (all in {@code org.fractalx.admin.user}):
 * <ul>
 *   <li>{@code AdminUserRepository}    — JPA repository for {@code AdminUser}</li>
 *   <li>{@code JpaUserStore}           — DB-backed {@code UserStoreService} ({@code @Profile("db")})</li>
 *   <li>{@code AdminSettingsRepository} — JPA repository for singleton {@code AdminSettings}</li>
 *   <li>{@code JpaSettingsStore}       — DB-backed {@code SettingsStoreService} ({@code @Profile("db")})</li>
 *   <li>{@code db/migration/V1__admin_schema.sql} — Flyway migration (MySQL 8+, PostgreSQL 15+, H2 2.x)</li>
 * </ul>
 */
class AdminDatabaseGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminDatabaseGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path pkg          = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".user");
        Path migrationDir = srcMainJava.getParent().resolve("resources/db/migration");
        Files.createDirectories(migrationDir);

        generateAdminUserRepository(pkg);
        generateJpaUserStore(pkg);
        generateAdminSettingsRepository(pkg);
        generateJpaSettingsStore(pkg);
        generateFlywayMigration(migrationDir);

        log.debug("Generated admin database persistence layer (JPA + Flyway)");
    }

    // -------------------------------------------------------------------------

    private void generateAdminUserRepository(Path pkg) throws IOException {
        String content = """
                package org.fractalx.admin.user;

                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                /**
                 * Spring Data JPA repository for {@link AdminUser}.
                 * Active only when the {@code db} Spring profile is enabled.
                 */
                @Repository
                public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
                }
                """;
        Files.writeString(pkg.resolve("AdminUserRepository.java"), content);
    }

    private void generateJpaUserStore(Path pkg) throws IOException {
        String content = """
                package org.fractalx.admin.user;

                import jakarta.annotation.PostConstruct;
                import org.springframework.context.annotation.Profile;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.stereotype.Component;
                import org.springframework.transaction.annotation.Transactional;

                import java.time.Instant;
                import java.util.*;

                /**
                 * Database-backed implementation of {@link UserStoreService}.
                 * Active when the {@code db} Spring profile is enabled.
                 *
                 * <p>Seeds {@code admin / admin123} (ROLE_ADMIN) and {@code viewer / viewer123}
                 * (ROLE_VIEWER) on first startup when the table is empty.
                 *
                 * <p>Activate: {@code -Dspring.profiles.active=db}
                 * or set {@code SPRING_PROFILES_ACTIVE=db} in the environment.
                 */
                @Component
                @Profile("db")
                public class JpaUserStore implements UserStoreService {

                    private final AdminUserRepository repo;
                    private final PasswordEncoder     encoder = new BCryptPasswordEncoder();

                    public JpaUserStore(AdminUserRepository repo) {
                        this.repo = repo;
                    }

                    @PostConstruct
                    @Transactional
                    public void init() {
                        if (repo.count() == 0) {
                            create("admin",  "admin123",  Set.of("ROLE_ADMIN"));
                            create("viewer", "viewer123", Set.of("ROLE_VIEWER"));
                        }
                    }

                    @Override
                    public Optional<AdminUser> findByUsername(String username) {
                        return repo.findById(username);
                    }

                    @Override
                    public List<AdminUser> findAll() {
                        return repo.findAll();
                    }

                    @Override
                    @Transactional
                    public AdminUser create(String username, String rawPassword, Set<String> roles) {
                        AdminUser user = new AdminUser(
                                username,
                                encoder.encode(rawPassword),
                                new HashSet<>(roles),
                                Instant.now().toString());
                        return repo.save(user);
                    }

                    @Override
                    @Transactional
                    public boolean changePassword(String username, String rawPassword) {
                        return repo.findById(username).map(u -> {
                            u.setPasswordHash(encoder.encode(rawPassword));
                            repo.save(u);
                            return true;
                        }).orElse(false);
                    }

                    @Override
                    @Transactional
                    public boolean updateRoles(String username, Set<String> roles) {
                        return repo.findById(username).map(u -> {
                            u.setRoles(new HashSet<>(roles));
                            repo.save(u);
                            return true;
                        }).orElse(false);
                    }

                    @Override
                    @Transactional
                    public boolean setActive(String username, boolean active) {
                        return repo.findById(username).map(u -> {
                            u.setActive(active);
                            repo.save(u);
                            return true;
                        }).orElse(false);
                    }

                    @Override
                    @Transactional
                    public boolean delete(String username) {
                        if (!repo.existsById(username)) return false;
                        repo.deleteById(username);
                        return true;
                    }

                    @Override
                    @Transactional
                    public void recordLogin(String username) {
                        repo.findById(username).ifPresent(u -> {
                            u.setLastLoginAt(Instant.now().toString());
                            repo.save(u);
                        });
                    }

                    @Override
                    public int count() {
                        return (int) repo.count();
                    }
                }
                """;
        Files.writeString(pkg.resolve("JpaUserStore.java"), content);
    }

    private void generateAdminSettingsRepository(Path pkg) throws IOException {
        String content = """
                package org.fractalx.admin.user;

                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                /**
                 * Spring Data JPA repository for the singleton {@link AdminSettings} row (id=1).
                 * Active only when the {@code db} Spring profile is enabled.
                 */
                @Repository
                public interface AdminSettingsRepository extends JpaRepository<AdminSettings, Integer> {
                }
                """;
        Files.writeString(pkg.resolve("AdminSettingsRepository.java"), content);
    }

    private void generateJpaSettingsStore(Path pkg) throws IOException {
        String content = """
                package org.fractalx.admin.user;

                import jakarta.annotation.PostConstruct;
                import org.springframework.context.annotation.Profile;
                import org.springframework.stereotype.Component;
                import org.springframework.transaction.annotation.Transactional;

                /**
                 * Database-backed implementation of {@link SettingsStoreService}.
                 * Persists a singleton settings row with {@code id=1} in {@code admin_settings}.
                 * Active when the {@code db} Spring profile is enabled.
                 */
                @Component
                @Profile("db")
                public class JpaSettingsStore implements SettingsStoreService {

                    private final AdminSettingsRepository repo;

                    public JpaSettingsStore(AdminSettingsRepository repo) {
                        this.repo = repo;
                    }

                    @PostConstruct
                    @Transactional
                    public void init() {
                        if (!repo.existsById(1)) {
                            AdminSettings defaults = new AdminSettings();
                            defaults.setId(1);
                            repo.save(defaults);
                        }
                    }

                    @Override
                    public AdminSettings get() {
                        return repo.findById(1).orElseGet(() -> {
                            AdminSettings s = new AdminSettings();
                            s.setId(1);
                            return repo.save(s);
                        });
                    }

                    @Override
                    @Transactional
                    public void update(AdminSettings settings) {
                        settings.setId(1);   // always singleton row
                        repo.save(settings);
                    }
                }
                """;
        Files.writeString(pkg.resolve("JpaSettingsStore.java"), content);
    }

    private void generateFlywayMigration(Path migrationDir) throws IOException {
        String content = """
                -- ================================================================
                -- FractalX Admin Service — Initial Schema                  V1
                -- Compatible with: MySQL 8+, PostgreSQL 15+, H2 2.x
                -- ================================================================

                -- Admin users (username is the PK / natural key)
                CREATE TABLE IF NOT EXISTS admin_users (
                    username        VARCHAR(100) NOT NULL,
                    password_hash   VARCHAR(255) NOT NULL,
                    created_at      VARCHAR(50),
                    last_login_at   VARCHAR(50),
                    active          BOOLEAN      NOT NULL DEFAULT TRUE,
                    PRIMARY KEY (username)
                );

                -- User roles — one-to-many via @ElementCollection
                CREATE TABLE IF NOT EXISTS admin_user_roles (
                    username  VARCHAR(100) NOT NULL,
                    role      VARCHAR(50)  NOT NULL,
                    PRIMARY KEY (username, role),
                    FOREIGN KEY (username)
                        REFERENCES admin_users(username)
                        ON DELETE CASCADE
                );

                -- Admin settings singleton (always id = 1)
                CREATE TABLE IF NOT EXISTS admin_settings (
                    id                  INT          NOT NULL,
                    site_name           VARCHAR(255) NOT NULL DEFAULT 'FractalX Admin',
                    theme               VARCHAR(50)  NOT NULL DEFAULT 'light',
                    session_timeout_min INT          NOT NULL DEFAULT 30,
                    default_alert_email VARCHAR(255) NOT NULL DEFAULT '',
                    maintenance_mode    BOOLEAN      NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (id)
                );
                """;
        Files.writeString(migrationDir.resolve("V1__admin_schema.sql"), content);
    }
}
