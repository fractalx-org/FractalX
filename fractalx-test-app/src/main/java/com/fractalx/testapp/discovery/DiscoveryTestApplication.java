package com.fractalx.testapp.discovery;

import com.fractalx.annotations.DiscoveryEnabled;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@DiscoveryEnabled
public class DiscoveryTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryTestApplication.class, args);
    }
}

@RestController
class DiscoveryTestController {

    @GetMapping("/health")
    public String health() {
        return "Discovery Test Service is running";
    }

    @GetMapping("/discovery/info")
    public String discoveryInfo() {
        return "Discovery endpoint for testing";
    }

    @GetMapping("/api/test")
    public String testEndpoint() {
        return "Test API endpoint";
    }
}