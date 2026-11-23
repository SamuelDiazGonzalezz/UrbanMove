package com.urbanmove.incidents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.urbanmove")
public class IncidentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentsApplication.class, args);
    }
}
