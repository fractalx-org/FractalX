package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates model classes (ServiceInfo) for the admin service. */
class AdminModelGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminModelGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path packagePath = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".model");
        String content = """
                package com.fractalx.admin.model;

                public class ServiceInfo {
                    private String name;
                    private String url;
                    private boolean running;

                    public ServiceInfo(String name, String url, boolean running) {
                        this.name    = name;
                        this.url     = url;
                        this.running = running;
                    }

                    public String  getName()    { return name; }
                    public void    setName(String name)       { this.name = name; }
                    public String  getUrl()     { return url; }
                    public void    setUrl(String url)         { this.url = url; }
                    public boolean isRunning()  { return running; }
                    public void    setRunning(boolean running){ this.running = running; }
                    public String  getStatus()      { return running ? "Running" : "Stopped"; }
                    public String  getStatusClass() { return running ? "success" : "danger"; }
                }
                """;
        Files.writeString(packagePath.resolve("ServiceInfo.java"), content);
        log.debug("Generated ServiceInfo model");
    }
}
