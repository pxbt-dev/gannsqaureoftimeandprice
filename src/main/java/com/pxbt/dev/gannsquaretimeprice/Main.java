package com.pxbt.dev.gannsquaretimeprice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class Main {
    public static void main(String[] args) throws Exception {
        // Start Spring Boot (for actuator/health checks)
        SpringApplication.run(Main.class, args);

        // Get port from environment (Railway sets this)
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            port = Integer.parseInt(envPort);
        }

        // Create and start your custom HTTP server
        HttpApiServer server = new HttpApiServer(port);

        System.out.println("Starting HttpApiServer on port: " + port);
        server.start();  // This should block
    }
}