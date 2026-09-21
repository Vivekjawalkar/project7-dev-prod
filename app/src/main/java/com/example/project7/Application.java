package com.example.project7;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Application {

    @GetMapping("/")
    public String home() {
        return "Project 7 - DEV to PRODUCTION Deployment - Version 1.0";
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
