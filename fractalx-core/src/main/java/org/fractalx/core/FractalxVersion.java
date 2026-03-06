package org.fractalx.core;

import java.io.InputStream;
import java.util.Properties;

/**
 * Single source of truth for the FractalX version at runtime.
 *
 * <p>The version is read from {@code fractalx.properties}, which is populated
 * by Maven resource filtering from {@code ${project.version}} at build time.
 * No version string is ever hardcoded in Java source.
 */
public final class FractalxVersion {

    private static final String VERSION;

    static {
        String v = "unknown";
        try (InputStream is = FractalxVersion.class.getResourceAsStream(
                "/org/fractalx/core/fractalx.properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(is);
                v = p.getProperty("version", "unknown");
            }
        } catch (Exception ignored) {}
        VERSION = v;
    }

    private FractalxVersion() {}

    /** Returns the current FractalX version, e.g. {@code 0.2.1}. */
    public static String get() { return VERSION; }

    /** Returns the version without the {@code -SNAPSHOT} suffix, e.g. {@code 0.2.0}. */
    public static String release() { return VERSION.replace("-SNAPSHOT", ""); }
}
