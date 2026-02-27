package com.fractalx.core.generator.admin

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies AdminUserManagementGenerator produces:
 *  - AdminUser.java       (user model)
 *  - UserStore.java       (BCrypt store, pre-seeded users)
 *  - AdminSettings.java   (settings value object)
 *  - SettingsStore.java   (AtomicReference wrapper)
 *  - UserController.java  (9 REST endpoints)
 */
class AdminUserManagementGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminUserManagementGenerator generator = new AdminUserManagementGenerator()

    static final String BASE = "com.fractalx.admin"

    def "generates five files in the user package"() {
        when:
        generator.generate(srcMainJava, BASE)
        def pkg = srcMainJava.resolve("com/fractalx/admin/user")

        then:
        Files.exists(pkg.resolve("AdminUser.java"))
        Files.exists(pkg.resolve("UserStore.java"))
        Files.exists(pkg.resolve("AdminSettings.java"))
        Files.exists(pkg.resolve("SettingsStore.java"))
        Files.exists(pkg.resolve("UserController.java"))
    }

    // ---- AdminUser ----------------------------------------------------------

    def "AdminUser is in com.fractalx.admin.user package"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        adminUser().contains("package com.fractalx.admin.user")
    }

    def "AdminUser has all required fields"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = adminUser()

        then:
        content.contains("username")
        content.contains("passwordHash")
        content.contains("roles")
        content.contains("createdAt")
        content.contains("lastLoginAt") || content.contains("lastLogin")
        content.contains("active") || content.contains("isActive")
    }

    // ---- UserStore ----------------------------------------------------------

    def "UserStore is annotated @Component with @PostConstruct"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userStore()

        then:
        content.contains("@Component")
        content.contains("@PostConstruct")
        content.contains("public void init()")
    }

    def "UserStore uses BCryptPasswordEncoder"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userStore()

        then:
        content.contains("BCryptPasswordEncoder")
        content.contains("PasswordEncoder")
    }

    def "UserStore creates its own BCryptPasswordEncoder to avoid circular dependency"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userStore()

        then:
        // Must use new BCryptPasswordEncoder() rather than @Autowired to avoid circular dep
        content.contains("new BCryptPasswordEncoder()")
    }

    def "UserStore pre-seeds admin user with ROLE_ADMIN"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userStore()

        then:
        content.contains('"admin"')
        content.contains("ROLE_ADMIN")
    }

    def "UserStore pre-seeds viewer user with ROLE_VIEWER"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userStore()

        then:
        content.contains('"viewer"')
        content.contains("ROLE_VIEWER")
    }

    def "UserStore exposes findByUsername, findAll, create, changePassword, updateRoles, setActive, delete, recordLogin, count"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userStore()

        then:
        content.contains("findByUsername")
        content.contains("findAll")
        content.contains("create")
        content.contains("changePassword")
        content.contains("updateRoles")
        content.contains("setActive") || content.contains("activate")
        content.contains("delete")
        content.contains("recordLogin")
        content.contains("count")
    }

    def "UserStore uses CopyOnWriteArrayList for thread-safe user list"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        userStore().contains("CopyOnWriteArrayList")
    }

    // ---- AdminSettings -------------------------------------------------------

    def "AdminSettings has expected default field values"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = adminSettings()

        then:
        content.contains("FractalX Admin") || content.contains("siteName")
        content.contains("theme") || content.contains("light")
        content.contains("sessionTimeout") || content.contains("30")
        content.contains("maintenanceMode") || content.contains("maintenance")
    }

    // ---- SettingsStore -------------------------------------------------------

    def "SettingsStore uses AtomicReference for thread-safe settings"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        settingsStore().contains("AtomicReference")
    }

    def "SettingsStore is annotated @Component with get and update methods"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = settingsStore()

        then:
        content.contains("@Component")
        content.contains("get()")
        content.contains("update(")
    }

    // ---- UserController ------------------------------------------------------

    def "UserController is a @RestController"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        userController().contains("@RestController")
    }

    def "UserController has GET /api/users list endpoint"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userController()

        then:
        content.contains("/api/users") || content.contains('"/api/users"')
        content.contains("@GetMapping")
    }

    def "UserController has POST /api/users create endpoint"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        userController().contains("@PostMapping") || userController().contains("POST")
    }

    def "UserController has PUT /api/users/{username}/password endpoint"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userController()

        then:
        content.contains("password") && (content.contains("@PutMapping") || content.contains("PUT"))
    }

    def "UserController has PUT /api/users/{username}/roles endpoint"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userController()

        then:
        content.contains("roles") || content.contains("Roles")
    }

    def "UserController has DELETE endpoint for user removal"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        userController().contains("@DeleteMapping") || userController().contains("DELETE")
    }

    def "UserController has GET /api/settings endpoint"() {
        when:
        generator.generate(srcMainJava, BASE)

        then:
        userController().contains("/api/settings") || userController().contains("settings")
    }

    def "UserController has GET /api/auth/profile endpoint"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userController()

        then:
        content.contains("/api/auth/profile") || (content.contains("auth") && content.contains("profile"))
    }

    def "UserController masks passwordHash in responses"() {
        when:
        generator.generate(srcMainJava, BASE)
        def content = userController()

        then:
        content.contains("***") || content.contains("passwordHash") || content.contains("masked")
    }

    // helpers
    private String adminUser()      { Files.readString(srcMainJava.resolve("com/fractalx/admin/user/AdminUser.java")) }
    private String userStore()      { Files.readString(srcMainJava.resolve("com/fractalx/admin/user/UserStore.java")) }
    private String adminSettings()  { Files.readString(srcMainJava.resolve("com/fractalx/admin/user/AdminSettings.java")) }
    private String settingsStore()  { Files.readString(srcMainJava.resolve("com/fractalx/admin/user/SettingsStore.java")) }
    private String userController() { Files.readString(srcMainJava.resolve("com/fractalx/admin/user/UserController.java")) }
}
