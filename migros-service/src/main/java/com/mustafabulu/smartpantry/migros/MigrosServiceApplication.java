package com.mustafabulu.smartpantry.migros;

import com.mustafabulu.smartpantry.SmartPantryApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class MigrosServiceApplication {
    private static final String SERVICE_PORT = "8081";

    private MigrosServiceApplication() {
    }

    public static void main(String[] args) {
        ensureDefaultPort(args);
        new SpringApplicationBuilder(SmartPantryApplication.class)
                .profiles("mg")
                .run(args);
    }

    private static void ensureDefaultPort(String[] args) {
        if (System.getProperty("server.port") != null) {
            return;
        }
        for (String arg : args) {
            if (arg.startsWith("--server.port=")) {
                return;
            }
        }
        System.setProperty("server.port", MigrosServiceApplication.SERVICE_PORT);
    }
}
