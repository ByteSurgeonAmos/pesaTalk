package com.pesatalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PesaTalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(PesaTalkApplication.class, args);
    }
}
