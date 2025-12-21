package com.fractalx.testapp.discovery;

import com.fractalx.core.discovery.DiscoveryInitializer;
import com.fractalx.core.discovery.ServiceInstance;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for discovery system
 */
@SpringBootTest(
        classes = DiscoveryTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "spring.application.name=test-discovery-service",
        "fractalx.discovery.enabled=true",
        "fractalx.discovery.mode=HYBRID",
        "fractalx.discovery.heartbeat-interval=5000",
        "fractalx.discovery.instance-ttl=15000",
        "fractalx.discovery.config-file=classpath:test-discovery-config.yml"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiscoveryIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryIntegrationTest.class);

    @LocalServerPort
    private int serverPort;

    private DiscoveryInitializer discoveryInitializer;

    @BeforeAll
    void setUpAll() {
        log.info("Starting discovery integration tests on port: {}", serverPort);
    }

    @BeforeEach
    void setUp() {
        log.info("Initializing discovery for test");

        // Initialize discovery with proper configuration
        Map<String, String> config = new HashMap<>();
        config.put("config-file", "classpath:test-discovery-config.yml");
        config.put("mode", "HYBRID");
        config.put("heartbeat-interval", "5000");
        config.put("instance-ttl", "15000");

        discoveryInitializer = new DiscoveryInitializer(5000, 15000);

        // Manually set up static config
        discoveryInitializer.getStaticConfig().addConfigFile("classpath:test-discovery-config.yml");
        discoveryInitializer.getStaticConfig().loadConfiguration();

        discoveryInitializer.initialize("test-discovery-service", "localhost", serverPort);

        // Give it time to initialize
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (discoveryInitializer != null) {
            discoveryInitializer.cleanup();
        }
        log.info("Discovery integration test completed");
    }

    @Test
    void testDiscoveryInitialization() {
        log.info("Testing discovery initialization");

        assertNotNull(discoveryInitializer);
        assertNotNull(discoveryInitializer.getClient());
        assertNotNull(discoveryInitializer.getRegistry());
        assertNotNull(discoveryInitializer.getStaticConfig());

        assertTrue(discoveryInitializer.isHealthy());

        log.info("✓ Discovery initialization test passed");
    }

    @Test
    void testStaticConfigLoading() {
        log.info("Testing static configuration loading");

        // Check static services from config file
        assertTrue(discoveryInitializer.getClient().isServiceAvailable("order-service"));
        assertTrue(discoveryInitializer.getClient().isServiceAvailable("payment-service"));
        assertTrue(discoveryInitializer.getClient().isServiceAvailable("inventory-service"));

        log.info("✓ Static configuration loading test passed");
    }

    @Test
    void testSelfRegistration() {
        log.info("Testing self-registration");

        // Check that service registered itself
        var instances = discoveryInitializer.getRegistry().getInstances("test-discovery-service");
        assertFalse(instances.isEmpty());

        ServiceInstance selfInstance = instances.get(0);
        assertEquals("test-discovery-service", selfInstance.getServiceName());
        assertEquals("localhost", selfInstance.getHost());
        assertEquals(serverPort, selfInstance.getPort());

        log.info("✓ Self-registration test passed");
    }

    @Test
    void testHeartbeatMechanism() throws InterruptedException {
        log.info("Testing heartbeat mechanism");

        // Get initial heartbeat time
        var instances = discoveryInitializer.getRegistry().getInstances("test-discovery-service");
        ServiceInstance instance = instances.get(0);
        long initialHeartbeat = instance.getLastHeartbeat();

        // Send heartbeat
        discoveryInitializer.sendHeartbeat();

        // Get updated heartbeat time
        instances = discoveryInitializer.getRegistry().getInstances("test-discovery-service");
        instance = instances.get(0);
        long updatedHeartbeat = instance.getLastHeartbeat();

        assertTrue(updatedHeartbeat > initialHeartbeat);

        log.info("✓ Heartbeat mechanism test passed");
    }

    @Test
    void testServiceDiscoveryClient() {
        log.info("Testing service discovery client");

        var client = discoveryInitializer.getClient();

        // Test service availability
        assertTrue(client.isServiceAvailable("order-service"));
        assertTrue(client.isServiceAvailable("payment-service"));

        // Test getting all services
        var services = client.getAllAvailableServices();
        assertTrue(services.contains("test-discovery-service"));

        log.info("✓ Service discovery client test passed");
    }

    @Test
    void testMultipleHeartbeats() throws InterruptedException {
        log.info("Testing multiple heartbeats");

        // Send multiple heartbeats
        for (int i = 0; i < 3; i++) {
            discoveryInitializer.sendHeartbeat();
            Thread.sleep(1000);
        }

        // Instance should still be healthy
        assertTrue(discoveryInitializer.isHealthy());

        log.info("✓ Multiple heartbeats test passed");
    }

    @Test
    void testCleanupOnShutdown() {
        log.info("Testing cleanup on shutdown");

        // Initial state
        assertFalse(discoveryInitializer.getRegistry().getInstances("test-discovery-service").isEmpty());

        // Cleanup
        discoveryInitializer.cleanup();

        // After cleanup, service should be deregistered
        assertTrue(discoveryInitializer.getRegistry().getInstances("test-discovery-service").isEmpty());

        log.info("✓ Cleanup on shutdown test passed");
    }
}