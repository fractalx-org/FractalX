package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates Spring Security configuration for the admin service. */
class AdminSecurityConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityConfigGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path packagePath = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".config");
        String content = """
                package com.fractalx.admin.config;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.core.userdetails.User;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.security.provisioning.InMemoryUserDetailsManager;
                import org.springframework.security.web.SecurityFilterChain;

                @Configuration
                @EnableWebSecurity
                public class SecurityConfig {

                    @Bean
                    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                        http
                            .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
                                .requestMatchers("/login").permitAll()
                                .anyRequest().authenticated()
                            )
                            .formLogin(form -> form
                                .loginPage("/login")
                                .defaultSuccessUrl("/dashboard", true)
                                .permitAll()
                            )
                            .logout(logout -> logout
                                .logoutSuccessUrl("/login?logout")
                                .permitAll()
                            );
                        return http.build();
                    }

                    @Bean
                    public UserDetailsService userDetailsService() {
                        UserDetails admin = User.builder()
                            .username("admin")
                            .password(passwordEncoder().encode("admin123"))
                            .roles("ADMIN")
                            .build();
                        return new InMemoryUserDetailsManager(admin);
                    }

                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }
                }
                """;
        Files.writeString(packagePath.resolve("SecurityConfig.java"), content);
        log.debug("Generated SecurityConfig");
    }
}
