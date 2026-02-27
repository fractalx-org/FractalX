package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates Spring Security configuration for the admin service.
 * <p>
 * The generated {@code SecurityConfig} delegates user authentication to {@code UserStore}
 * (multi-user, BCrypt) instead of a static in-memory user. Role-based access control is
 * applied to sensitive admin API paths.
 */
class AdminSecurityConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityConfigGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path packagePath = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".config");
        String content = """
                package com.fractalx.admin.config;

                import com.fractalx.admin.user.AdminUser;
                import com.fractalx.admin.user.UserStore;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.core.authority.SimpleGrantedAuthority;
                import org.springframework.security.core.userdetails.User;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.core.userdetails.UsernameNotFoundException;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.security.web.SecurityFilterChain;

                import java.util.stream.Collectors;

                @Configuration
                @EnableWebSecurity
                public class SecurityConfig {

                    @Bean
                    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserStore userStore)
                            throws Exception {
                        http
                            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                            .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
                                .requestMatchers("/login").permitAll()
                                .requestMatchers("/api/users/**").hasRole("ADMIN")
                                .requestMatchers("/api/config/**").hasAnyRole("ADMIN", "OPERATOR")
                                .anyRequest().authenticated()
                            )
                            .formLogin(form -> form
                                .loginPage("/login")
                                .successHandler((request, response, auth) -> {
                                    userStore.recordLogin(auth.getName());
                                    response.sendRedirect("/dashboard");
                                })
                                .permitAll()
                            )
                            .logout(logout -> logout
                                .logoutSuccessUrl("/login?logout")
                                .permitAll()
                            );
                        return http.build();
                    }

                    /**
                     * Delegates user lookup to {@link UserStore} — supports multiple users
                     * with BCrypt-hashed passwords and multiple roles.
                     */
                    @Bean
                    public UserDetailsService userDetailsService(UserStore userStore) {
                        return username -> userStore.findByUsername(username)
                                .filter(AdminUser::isActive)
                                .map(u -> User.withUsername(u.getUsername())
                                        .password(u.getPasswordHash())
                                        .authorities(u.getRoles().stream()
                                                .map(SimpleGrantedAuthority::new)
                                                .collect(Collectors.toList()))
                                        .build())
                                .orElseThrow(() ->
                                        new UsernameNotFoundException("User not found: " + username));
                    }

                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }
                }
                """;
        Files.writeString(packagePath.resolve("SecurityConfig.java"), content);
        log.debug("Generated SecurityConfig (UserStore-backed)");
    }
}
