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
        System.setProperty("server.address", "0.0.0.0");
        System.setProperty("server.port", "8080");
        SpringApplication.run(CelebrityShortestPathFinderApplication.class, args);
    }

    @Configuration
    public static class AppConfig {

        @Bean
        public Graph graph() throws Exception {
            return new Graph();
        }
        
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }
} 