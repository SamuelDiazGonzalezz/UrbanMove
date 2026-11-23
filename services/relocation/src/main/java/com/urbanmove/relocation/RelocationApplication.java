package com.urbanmove.relocation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.urbanmove")
public class RelocationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelocationApplication.class, args);
    }
}
