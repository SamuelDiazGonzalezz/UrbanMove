package com.urbanmove.profiles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.urbanmove")
public class UserProfilesApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserProfilesApplication.class, args);
    }
}
