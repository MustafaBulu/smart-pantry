package com.mustafabulu.smartpantry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartPantryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPantryApplication.class, args);
    }

}
