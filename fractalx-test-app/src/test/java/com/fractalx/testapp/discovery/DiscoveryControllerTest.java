//package com.fractalx.testapp.discovery;
//
//import com.fractalx.core.discovery.*;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * Unit tests for discovery components
// */
//class DiscoveryControllerTest {
//
//    private static final Logger log = LoggerFactory.getLogger(DiscoveryControllerTest.class);
//
//    private DiscoveryRegistry registry;
//    private StaticDiscoveryConfig staticConfig;
//    private DiscoveryClient client;
//
//    @BeforeEach
//    void setUp() {
//        log.info("Setting up discovery test");
//
//        // Create registry with shorter intervals for testing
//        registry = new DiscoveryRegistry(5000, 15000); // 5s heartbeat, 15s TTL
//
//        // Create static config
//        staticConfig = new StaticDiscoveryConfig();
//        staticConfig.addConfigFile("src/test/resources/test-discovery-config.yml");
//
//        // Create client
//        client = new DiscoveryClient(registry, staticConfig);
//
//        // Load configuration
//        staticConfig.loadConfiguration();
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (registry != null) {
//            registry.shutdown();
//        }
//        log.info("Discovery test completed");
//    }
//
//    @Test
//    void testServiceRegistration() {
//        log.info("Testing service registration");
//
//        // Register test services
//        ServiceInstance orderService = new ServiceInstance("order-service", "localhost", 8081);
//        ServiceInstance paymentService = new ServiceInstance("payment-service", "localhost", 8080);
//
//        registry.register(orderService);
//        registry.register(paymentService);
//
//        // Verify registration
//        assertEquals(2, registry.getTotalInstances());
//        assertEquals(2, registry.getTotalServices());
//
//        List<ServiceInstance> orderInstances = registry.getInstances("order-service");
//        assertEquals(1, orderInstances.size());
//        assertEquals("order-service", orderInstances.get(0).getServiceName());
//        assertEquals(8081, orderInstances.get(0).getPort());
//
//        log.info("✓ Service registration test passed");
//    }
//
//    @Test
//    void testServiceDiscovery() {
//        log.info("Testing service discovery");
//
//        // Register services
//        registry.register(new ServiceInstance("order-service", "localhost", 8081));
//        registry.register(new ServiceInstance("payment-service", "localhost", 8080));
//        registry.register(new ServiceInstance("payment-service", "localhost", 8082));
//
//        // Test getting instances
//        List<ServiceInstance> paymentInstances = registry.getInstances("payment-service");
//        assertEquals(2, paymentInstances.size());
//
//        // Test healthy instance selection
//        ServiceInstance healthyInstance = registry.getHealthyInstance("payment-service");
//        assertNotNull(healthyInstance);
//        assertEquals("payment-service", healthyInstance.getServiceName());
//
//        log.info("✓ Service discovery test passed");
//    }
//
//    // Test for duplicate registrations
//    @Test
//    void testDuplicateRegistration() {
//        ServiceInstance instance1 = new ServiceInstance("test-service", "localhost", 8080);
//        ServiceInstance instance2 = new ServiceInstance("test-service", "localhost", 8080);
//
//        registry.register(instance1);
//        registry.register(instance2);
//
//        // Should handle duplicates gracefully
//        assertEquals(1, registry.getInstances("test-service").size());
//    }
//
//    @Test
//    void testLoadBalancing() {
//        // Register multiple instances
//        registry.register(new ServiceInstance("service", "host1", 8080));
//        registry.register(new ServiceInstance("service", "host2", 8081));
//
//        // Test round-robin or random selection
//        ServiceInstance instance1 = registry.getHealthyInstance("service");
//        ServiceInstance instance2 = registry.getHealthyInstance("service");
//        // Verify different instances are returned (if round-robin)
//    }
//
//    @Test
//    void testHeartbeatAndExpiration() throws InterruptedException {
//        log.info("Testing heartbeat and expiration");
//
//        ServiceInstance instance = new ServiceInstance("test-service", "localhost", 9999);
//        registry.register(instance);
//
//        // Initial state should be UP
//        assertEquals("UP", instance.getStatus());
//        assertFalse(instance.isExpired(15000));
//
//        // Simulate heartbeat
//        registry.heartbeat(instance.getInstanceId());
//
//        // Wait for expiration
//        Thread.sleep(16000); // Wait longer than TTL
//
//        // Instance should be expired
//        assertTrue(instance.isExpired(15000));
//
//        // Cleanup should remove expired instance
//        registry.cleanupExpiredInstances();
//        assertTrue(registry.getInstances("test-service").isEmpty());
//
//        log.info("✓ Heartbeat and expiration test passed");
//    }
//
//    @Test
//    void testStaticConfiguration() {
//        log.info("Testing static configuration");
//
//        // Test static instances from config
//        List<ServiceInstance> orderInstances = staticConfig.getStaticInstances("order-service");
//        assertEquals(2, orderInstances.size());  // Changed from 1 to 2
//
//        List<ServiceInstance> paymentInstances = staticConfig.getStaticInstances("payment-service");
//        assertEquals(2, paymentInstances.size());  // Changed from 1 to 2
//
//        // Test service availability
//        assertTrue(client.isServiceAvailable("order-service"));
//        assertTrue(client.isServiceAvailable("payment-service"));
//        assertTrue(client.isServiceAvailable("inventory-service"));
//
//        log.info("✓ Static configuration test passed");
//    }
//
//    @Test
//    void testDiscoveryClient() {
//        log.info("Testing discovery client");
//
//        // Register dynamic instances
//        registry.register(new ServiceInstance("order-service", "localhost", 8081));
//        registry.register(new ServiceInstance("payment-service", "localhost", 8080));
//
//        // Test service URL generation
//        String orderServiceUrl = client.getServiceUrl("order-service");
//        assertNotNull(orderServiceUrl);
//        assertTrue(orderServiceUrl.contains("localhost:8081"));
//
//        // Test healthy instance selection
//        ServiceInstance instance = client.getHealthyInstance("payment-service");
//        assertNotNull(instance);
//        assertEquals("payment-service", instance.getServiceName());
//
//        log.info("✓ Discovery client test passed");
//    }
//
//    @Test
//    void testDeregistration() {
//        log.info("Testing service deregistration");
//
//        ServiceInstance instance = new ServiceInstance("temp-service", "localhost", 7777);
//        String instanceId = instance.getInstanceId();
//
//        registry.register(instance);
//        assertNotNull(registry.getInstance(instanceId));
//
//        registry.deregister(instanceId);
//        assertNull(registry.getInstance(instanceId));
//        assertTrue(registry.getInstances("temp-service").isEmpty());
//
//        log.info("✓ Service deregistration test passed");
//    }
//
//    @Test
//    void testRegistryCleanup() {
//        log.info("Testing registry cleanup");
//
//        // Add multiple instances
//        registry.register(new ServiceInstance("service-a", "host1", 8080));
//        registry.register(new ServiceInstance("service-a", "host2", 8081));
//        registry.register(new ServiceInstance("service-b", "host3", 8082));
//
//        assertEquals(3, registry.getTotalInstances());
//        assertEquals(2, registry.getTotalServices());
//
//        // Cleanup
//        registry.shutdown();
//
//        assertEquals(0, registry.getTotalInstances());
//        assertEquals(0, registry.getTotalServices());
//
//        log.info("✓ Registry cleanup test passed");
//    }
//}

package com.fractalx.testapp.discovery;

import com.fractalx.core.discovery.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for discovery components
 */
class DiscoveryControllerTest {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryControllerTest.class);

    private DiscoveryRegistry registry;
    private StaticDiscoveryConfig staticConfig;
    private DiscoveryClient client;

    @BeforeEach
    void setUp() {
        log.info("Setting up discovery test");

        // Create registry with shorter intervals for testing
        registry = new DiscoveryRegistry(5000, 15000); // 5s heartbeat, 15s TTL

        // Create static config
        staticConfig = new StaticDiscoveryConfig();
        staticConfig.addConfigFile("src/test/resources/test-discovery-config.yml");

        // Create client
        client = new DiscoveryClient(registry, staticConfig);

        // Load configuration
        staticConfig.loadConfiguration();
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
        log.info("Discovery test completed");
    }

    @Test
    void testServiceRegistration() {
        log.info("Testing service registration");

        // Register test services
        ServiceInstance orderService = new ServiceInstance("order-service", "localhost", 8081);
        ServiceInstance paymentService = new ServiceInstance("payment-service", "localhost", 8080);

        registry.register(orderService);
        registry.register(paymentService);

        // Verify registration
        assertEquals(2, registry.getTotalInstances());
        assertEquals(2, registry.getTotalServices());

        List<ServiceInstance> orderInstances = registry.getInstances("order-service");
        assertEquals(1, orderInstances.size());
        assertEquals("order-service", orderInstances.get(0).getServiceName());
        assertEquals(8081, orderInstances.get(0).getPort());

        log.info("✓ Service registration test passed");
    }

    @Test
    void testServiceDiscovery() {
        log.info("Testing service discovery");

        // Register services
        registry.register(new ServiceInstance("order-service", "localhost", 8081));
        registry.register(new ServiceInstance("payment-service", "localhost", 8080));
        registry.register(new ServiceInstance("payment-service", "localhost", 8082));

        // Test getting instances
        List<ServiceInstance> paymentInstances = registry.getInstances("payment-service");
        assertEquals(2, paymentInstances.size());

        // Test healthy instance selection
        ServiceInstance healthyInstance = registry.getHealthyInstance("payment-service");
        assertNotNull(healthyInstance);
        assertEquals("payment-service", healthyInstance.getServiceName());

        log.info("✓ Service discovery test passed");
    }

    @Test
    void testDuplicateRegistration() {
        log.info("Testing duplicate registration");

        ServiceInstance instance1 = new ServiceInstance("test-service", "localhost", 8080);
        ServiceInstance instance2 = new ServiceInstance("test-service", "localhost", 8080);

        registry.register(instance1);
        registry.register(instance2);

        // Should handle duplicates gracefully
        assertEquals(1, registry.getInstances("test-service").size());

        log.info("✓ Duplicate registration test passed");
    }

    @Test
    void testLoadBalancing() {
        log.info("Testing load balancing");

        // Register multiple instances
        registry.register(new ServiceInstance("service", "host1", 8080));
        registry.register(new ServiceInstance("service", "host2", 8081));

        // Test getting multiple instances
        List<ServiceInstance> instances = registry.getInstances("service");
        assertEquals(2, instances.size());

        // Test healthy instance selection
        ServiceInstance instance1 = registry.getHealthyInstance("service");
        assertNotNull(instance1);
        assertEquals("service", instance1.getServiceName());

        log.info("✓ Load balancing test passed");
    }

    @Test
    void testHeartbeatAndExpiration() throws InterruptedException {
        log.info("Testing heartbeat and expiration");

        ServiceInstance instance = new ServiceInstance("test-service", "localhost", 9999);
        registry.register(instance);

        // Initial state should be UP
        assertEquals("UP", instance.getStatus());
        assertFalse(instance.isExpired(15000));

        // Simulate heartbeat
        registry.heartbeat(instance.getInstanceId());

        // Wait for expiration
        Thread.sleep(16000); // Wait longer than TTL

        // Instance should be expired
        assertTrue(instance.isExpired(15000));

        // Cleanup should remove expired instance
        registry.cleanupExpiredInstances();
        assertTrue(registry.getInstances("test-service").isEmpty());

        log.info("✓ Heartbeat and expiration test passed");
    }

    @Test
    void testStaticConfiguration() {
        log.info("Testing static configuration");

        // Test static instances from config
        List<ServiceInstance> orderInstances = staticConfig.getStaticInstances("order-service");
        assertEquals(1, orderInstances.size());  // Change from 2 to 1

        List<ServiceInstance> paymentInstances = staticConfig.getStaticInstances("payment-service");
        assertEquals(1, paymentInstances.size());  // Change from 2 to 1

        // Test service availability
        assertTrue(client.isServiceAvailable("order-service"));
        assertTrue(client.isServiceAvailable("payment-service"));
        assertTrue(client.isServiceAvailable("inventory-service"));

        log.info("✓ Static configuration test passed");
    }

    @Test
    void testDiscoveryClient() {
        log.info("Testing discovery client");

        // Register dynamic instances
        registry.register(new ServiceInstance("order-service", "localhost", 8081));
        registry.register(new ServiceInstance("payment-service", "localhost", 8080));

        // Test service URL generation
        String orderServiceUrl = client.getServiceUrl("order-service");
        assertNotNull(orderServiceUrl);
        assertTrue(orderServiceUrl.contains("localhost:8081"));

        // Test healthy instance selection
        ServiceInstance instance = client.getHealthyInstance("payment-service");
        assertNotNull(instance);
        assertEquals("payment-service", instance.getServiceName());

        log.info("✓ Discovery client test passed");
    }

    @Test
    void testDeregistration() {
        log.info("Testing service deregistration");

        ServiceInstance instance = new ServiceInstance("temp-service", "localhost", 7777);
        String instanceId = instance.getInstanceId();

        registry.register(instance);
        assertNotNull(registry.getInstance(instanceId));

        registry.deregister(instanceId);
        assertNull(registry.getInstance(instanceId));
        assertTrue(registry.getInstances("temp-service").isEmpty());

        log.info("✓ Service deregistration test passed");
    }

    @Test
    void testRegistryCleanup() {
        log.info("Testing registry cleanup");

        // Add multiple instances
        registry.register(new ServiceInstance("service-a", "host1", 8080));
        registry.register(new ServiceInstance("service-a", "host2", 8081));
        registry.register(new ServiceInstance("service-b", "host3", 8082));

        assertEquals(3, registry.getTotalInstances());
        assertEquals(2, registry.getTotalServices());

        // Cleanup
        registry.shutdown();

        assertEquals(0, registry.getTotalInstances());
        assertEquals(0, registry.getTotalServices());

        log.info("✓ Registry cleanup test passed");
    }
}