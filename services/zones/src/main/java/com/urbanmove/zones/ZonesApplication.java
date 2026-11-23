package com.urbanmove.zones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.urbanmove")
public class ZonesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZonesApplication.class, args);
    }
}
