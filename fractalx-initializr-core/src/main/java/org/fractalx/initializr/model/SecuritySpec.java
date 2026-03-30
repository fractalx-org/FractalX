package org.fractalx.initializr.model;

/**
 * Security profile for the generated application.
 * {@code type}: {@code none} | {@code jwt} | {@code oauth2} | {@code apikey}.
 */
public class SecuritySpec {

    private String type = "none";

    public String getType()          { return type; }
    public void   setType(String v)  { this.type = v; }

    public boolean isJwt()    { return "jwt".equalsIgnoreCase(type); }
    public boolean isOAuth2() { return "oauth2".equalsIgnoreCase(type); }
    public boolean isApiKey() { return "apikey".equalsIgnoreCase(type); }
    public boolean isNone()   { return "none".equalsIgnoreCase(type) || type == null || type.isBlank(); }
}
