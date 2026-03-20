package com.mustafabulu.smartpantry.migros;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrosServiceApplicationTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("server.port");
    }

    @Test
    void ensureDefaultPortSetsPortWhenMissing() throws Exception {
        Method method = MigrosServiceApplication.class.getDeclaredMethod("ensureDefaultPort", String[].class);
        method.setAccessible(true);

        method.invoke(null, (Object) new String[0]);

        assertEquals("8081", System.getProperty("server.port"));
    }

    @Test
    void ensureDefaultPortKeepsExplicitArgument() throws Exception {
        Method method = MigrosServiceApplication.class.getDeclaredMethod("ensureDefaultPort", String[].class);
        method.setAccessible(true);

        method.invoke(null, (Object) new String[]{"--server.port=9090"});

        assertEquals(null, System.getProperty("server.port"));
    }
}
