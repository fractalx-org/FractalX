package org.fractalx.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking decomposed FractalX services
 */
@Component
public class FractalServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(FractalServiceRegistry.class);
    private final Map<String, ServiceEndpoint> services = new ConcurrentHashMap<>();

    public void registerService(String serviceName, String host, int port) {
        ServiceEndpoint endpoint = new ServiceEndpoint(serviceName, host, port);
        services.put(serviceName, endpoint);
        log.info("Registered service: {} at {}:{}", serviceName, host, port);
    }

    public ServiceEndpoint getService(String serviceName) {
        return services.get(serviceName);
    }

    public Map<String, ServiceEndpoint> getAllServices() {
        return new ConcurrentHashMap<>(services);
    }

    public void unregisterService(String serviceName) {
        ServiceEndpoint removed = services.remove(serviceName);
        if (removed != null) {
            log.info("Unregistered service: {}", serviceName);
        }
    }

    public boolean isServiceRegistered(String serviceName) {
        return services.containsKey(serviceName);
    }

    public static class ServiceEndpoint {
        private final String name;
        private final String host;
        private final int port;

        public ServiceEndpoint(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getUrl() {
            return "http://" + host + ":" + port;
        }

        @Override
        public String toString() {
            return "ServiceEndpoint{" +
                    "name='" + name + '\'' +
                    ", url='" + getUrl() + '\'' +
                    '}';
        }
    }
}