package com.companyos.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.companyos")
@EnableScheduling
public class CompanyOsBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompanyOsBackendApplication.class, args);
    }
}
