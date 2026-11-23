package com.urbanmove.availability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.urbanmove")
public class AvailabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvailabilityApplication.class, args);
    }
}
