package com.urbanmove.rides;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.urbanmove")
public class RidesApplication {

    public static void main(String[] args) {
        SpringApplication.run(RidesApplication.class, args);
    }
}
