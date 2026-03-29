package org.fractalx.core.validation;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.model.FractalModule;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable context passed to every {@link ValidationRule}.
 *
 * @param modules    all modules discovered by {@code ModuleAnalyzer}
 * @param sourceRoot path to the monolith's {@code src/main/java} directory
 * @param config     platform config read from {@code fractalx-config.yml} (or defaults)
 */
public record ValidationContext(
        List<FractalModule> modules,
        Path                sourceRoot,
        FractalxConfig      config
) {}
