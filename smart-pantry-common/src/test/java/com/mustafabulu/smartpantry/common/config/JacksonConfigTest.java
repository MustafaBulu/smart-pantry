package com.mustafabulu.smartpantry.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JacksonConfigTest {

    @Test
    void objectMapperFindsRegisteredModules() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();

        String json = objectMapper.writeValueAsString(Map.of("date", LocalDate.of(2026, 3, 20)));

        assertFalse(objectMapper.getRegisteredModuleIds().isEmpty());
        assertEquals(
                LocalDate.of(2026, 3, 20),
                objectMapper.readTree(json).path("date").traverse(objectMapper).readValueAs(LocalDate.class)
        );
    }
}
