package org.fractalx.core.generator.admin

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AdminAnalyticsGenerator produces all 3 required classes
 * in the org.fractalx.admin.analytics package: MetricsHistoryStore,
 * MetricsCollector, and AnalyticsController.
 */
class AdminAnalyticsGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminAnalyticsGenerator generator = new AdminAnalyticsGenerator()
    String basePackage = "org.fractalx.admin"

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private Path analyticsPkg() {
        srcMainJava.resolve("org/fractalx/admin/analytics")
    }

    private String read(String name) {
        Files.readString(analyticsPkg().resolve(name))
    }

    def "all 3 analytics files are created"() {
        when:
        generator.generate(srcMainJava, basePackage, [order, payment])

        then:
        ["MetricsHistoryStore.java", "MetricsCollector.java", "AnalyticsController.java"].every {
            Files.exists(analyticsPkg().resolve(it))
        }
    }

    def "MetricsHistoryStore is a @Component with circular buffer capped at 60 points"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsHistoryStore.java")
        c.contains("@Component")
        c.contains("60")
        c.contains("ArrayDeque")
    }

    def "MetricsHistoryStore defines MetricsSnapshot record with cpu, heap, rps, p99 fields"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsHistoryStore.java")
        c.contains("record MetricsSnapshot")
        c.contains("cpuPct")
        c.contains("heapUsedMb")
        c.contains("rps")
        c.contains("p99Ms")
    }

    def "MetricsHistoryStore provides record, getHistory, getLatest, getLatestAll, and getTrackedServices"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsHistoryStore.java")
        c.contains("record(")
        c.contains("getHistory")
        c.contains("getLatest")
        c.contains("getLatestAll")
        c.contains("getTrackedServices")
    }

    def "MetricsCollector is a @Component with @Scheduled collection every 15 seconds"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsCollector.java")
        c.contains("@Component")
        c.contains("@Scheduled")
        c.contains("15")
    }

    def "MetricsCollector polls cpu, heap, threads, and http request metrics"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsCollector.java")
        c.contains("process.cpu.usage")
        c.contains("jvm.memory.used")
        c.contains("jvm.threads.live")
        c.contains("http.server.requests")
    }

    def "MetricsCollector uses actuator/metrics endpoint for data collection"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsCollector.java")
        c.contains("actuator/metrics")
        c.contains("RestTemplate")
    }

    def "MetricsCollector calculates delta RPS from request count differences"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("MetricsCollector.java")
        c.contains("prevCounts") || c.contains("prev")
        c.contains("rps")
    }

    def "AnalyticsController is a @RestController mapped to /api/analytics"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("@RestController")
        c.contains("/api/analytics")
    }

    def "AnalyticsController exposes GET /overview with aggregate metrics"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("/overview")
        c.contains("totalRps")
        c.contains("avgCpuPct")
    }

    def "AnalyticsController exposes GET /realtime with per-service current metrics"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("/realtime")
        c.contains("realtime")
    }

    def "AnalyticsController exposes GET /history/{service} with time-series data"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("/history")
        c.contains("history(")
        c.contains("labels")
    }

    def "AnalyticsController exposes GET /trends with multi-service time-series"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("/trends")
        c.contains("datasets")
    }

    def "AnalyticsController exposes SSE GET /stream endpoint"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("/stream")
        c.contains("SseEmitter")
        c.contains("TEXT_EVENT_STREAM")
    }

    def "AnalyticsController uses a daemon thread for SSE push"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("AnalyticsController.java")
        c.contains("setDaemon") || c.contains("daemon")
    }
}
