package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One step in the project-scaffold pipeline.
 * Each implementation writes one or more files under {@link InitializerContext#outputRoot()}.
 */
public interface InitializerFileGenerator {

    String label();

    void generate(InitializerContext ctx) throws IOException;

    // ── Shared write helpers ───────────────────────────────────────────────────

    default void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    default String indent(int spaces, String text) {
        String pad = " ".repeat(spaces);
        return text.lines()
                   .map(l -> l.isEmpty() ? l : pad + l)
                   .collect(java.util.stream.Collectors.joining("\n"));
    }
}
