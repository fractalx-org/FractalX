package org.fractalx.runtime;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Lightweight principal reconstructed from the FractalX API Gateway's Internal Call Token.
 *
 * <p>After decomposition, the monolith's custom {@code User} / {@code UserDetails} entity no
 * longer exists inside every service (it belongs exclusively to the auth-service). This class
 * takes its place as the {@code @AuthenticationPrincipal} object, populated by the generated
 * {@code GatewayAuthHeaderFilter} from the signed {@code X-Internal-Token} JWT claims.
 *
 * <p>Implements {@link UserDetails} so it is a drop-in replacement wherever Spring Security
 * expects a principal — {@code @AuthenticationPrincipal}, {@code @PreAuthorize} SpEL
 * expressions, {@code SecurityContextHolder}, and method-level security.
 *
 * <h3>Standard claims (always present)</h3>
 * <ul>
 *   <li>{@link #getId()} — the user's unique identifier ({@code sub} claim)</li>
 *   <li>{@link #getAuthorities()} — granted authorities parsed from {@code roles} claim</li>
 * </ul>
 *
 * <h3>Extended claims (present when the gateway forwards them)</h3>
 * <ul>
 *   <li>{@link #getUsername()} — falls back to {@code id} when not available</li>
 *   <li>{@link #getEmail()} — {@code null} when not available</li>
 *   <li>{@link #getAttributes()} — all non-standard JWT claims as a generic map</li>
 * </ul>
 *
 * <h3>SpEL examples</h3>
 * <pre>
 * {@literal @}PreAuthorize("principal.id == #userId")
 * {@literal @}PreAuthorize("principal.hasRole('ADMIN')")
 * {@literal @}PreAuthorize("principal.getAttribute('tenantId') == #tenantId")
 * </pre>
 */
public class GatewayPrincipal implements UserDetails {

    private final String id;
    private final String username;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    public GatewayPrincipal(String id,
                            String username,
                            String email,
                            Collection<? extends GrantedAuthority> authorities,
                            Map<String, Object> attributes) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
    }

    /** The user's unique identifier — always the JWT {@code sub} claim. */
    public String getId() {
        return id;
    }

    /**
     * Returns the username if forwarded by the gateway, otherwise falls back to {@link #getId()}.
     * This ensures compatibility with code that calls {@code user.getUsername()}.
     */
    @Override
    public String getUsername() {
        return username != null ? username : id;
    }

    /** The user's email address, or {@code null} if not forwarded by the gateway. */
    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * All non-standard JWT claims as a read-only map.
     * Use this to access custom claims the gateway forwards from the original user token
     * (e.g. {@code tenantId}, {@code department}, {@code customerId}).
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Convenience accessor for a single attribute.
     *
     * @return the attribute value, or {@code null} if not present
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Checks whether this principal has the given role.
     * Accepts both bare role names ({@code "ADMIN"}) and prefixed
     * ({@code "ROLE_ADMIN"}) — useful in {@code @PreAuthorize} SpEL.
     */
    public boolean hasRole(String role) {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)
                        || a.getAuthority().equals(role));
    }

    // ── UserDetails contract — non-applicable fields ─────────────────────────

    /** Not applicable — decomposed services never validate credentials. */
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "GatewayPrincipal{id='" + id + "', username='" + getUsername() + "'}";
    }
}
