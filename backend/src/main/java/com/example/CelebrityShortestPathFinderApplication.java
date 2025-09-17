package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class CelebrityShortestPathFinderApplication {

    public static void main(String[] args) {
        // Force server to bind to 0.0.0.0 instead of localhost
        System.setProperty("server.address", "0.0.0.0");
        System.out.println("DEBUG: server.address system property set to: " + System.getProperty("server.address"));
        System.out.println("DEBUG: SERVER_ADDRESS env var: " + System.getenv("SERVER_ADDRESS"));
        SpringApplication.run(CelebrityShortestPathFinderApplication.class, args);
    }

    @Configuration
    public static class AppConfig {

        @Bean
        public Graph graph() throws java.io.IOException {
            // Build or load cached graph at startup
            return new Graph();
        }
        
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }
} 