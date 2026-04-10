package org.fractalx.core.generator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the ServiceRegistration model class for the fractalx-registry service. */
class RegistryModelGenerator {

    private static final Logger log = LoggerFactory.getLogger(RegistryModelGenerator.class);

    void generate(Path srcMainJava) throws IOException {
        Path pkg = createPkg(srcMainJava, "org/fractalx/registry/model");

        String content = """
                package org.fractalx.registry.model;

                import jakarta.validation.constraints.NotBlank;
                import java.time.Instant;

                public class ServiceRegistration {

                    @NotBlank(message = "Service name must not be blank")
                    private String name;

                    @NotBlank(message = "Service host must not be blank")
                    private String host;
                    private int port;
                    private int grpcPort;
                    private String healthUrl;
                    private String status;
                    private Instant lastSeen;
                    private Instant registeredAt;

                    public ServiceRegistration() {
                        this.status = "UNKNOWN";
                        this.registeredAt = Instant.now();
                        this.lastSeen = Instant.now();
                    }

                    public ServiceRegistration(String name, String host, int port, int grpcPort, String healthUrl) {
                        this();
                        this.name = name;
                        this.host = host;
                        this.port = port;
                        this.grpcPort = grpcPort;
                        this.healthUrl = healthUrl;
                    }

                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                    public String getHost() { return host; }
                    public void setHost(String host) { this.host = host; }
                    public int getPort() { return port; }
                    public void setPort(int port) { this.port = port; }
                    public int getGrpcPort() { return grpcPort; }
                    public void setGrpcPort(int grpcPort) { this.grpcPort = grpcPort; }
                    public String getHealthUrl() { return healthUrl; }
                    public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }
                    public String getStatus() { return status; }
                    public void setStatus(String status) { this.status = status; }
                    public Instant getLastSeen() { return lastSeen; }
                    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
                    public Instant getRegisteredAt() { return registeredAt; }
                    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

                    public String getBaseUrl() { return "http://" + host + ":" + port; }
                }
                """;

        Files.writeString(pkg.resolve("ServiceRegistration.java"), content);
        log.debug("Generated ServiceRegistration");
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
