package com.mustafabulu.smartpantry.yemeksepeti;

import com.mustafabulu.smartpantry.SmartPantryApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class YemeksepetiServiceApplication {
    private static final String SERVICE_PORT = "8082";

    private YemeksepetiServiceApplication() {
    }

    public static void main(String[] args) {
        ensureDefaultPort(args, SERVICE_PORT);
        new SpringApplicationBuilder(SmartPantryApplication.class)
                .profiles("ys")
                .run(args);
    }

    private static void ensureDefaultPort(String[] args, String port) {
        if (System.getProperty("server.port") != null) {
            return;
        }
        for (String arg : args) {
            if (arg.startsWith("--server.port=")) {
                return;
            }
        }
        System.setProperty("server.port", port);
    }
}
