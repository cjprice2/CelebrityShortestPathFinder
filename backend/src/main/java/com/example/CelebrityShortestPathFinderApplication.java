package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
public class CelebrityShortestPathFinderApplication {

    private static Graph graphInstance;

    public static void main(String[] args) {
        // Force server to bind to 0.0.0.0 instead of localhost
        System.setProperty("server.address", "0.0.0.0");
        System.setProperty("server.port", "8080");
        System.out.println("DEBUG: server.address system property set to: " + System.getProperty("server.address"));
        System.out.println("DEBUG: SERVER_ADDRESS env var: " + System.getenv("SERVER_ADDRESS"));
        
        SpringApplication.run(CelebrityShortestPathFinderApplication.class, args);
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Start loading the graph asynchronously after the application is ready
        if (graphInstance != null) {
            System.out.println("Starting async graph loading...");
            graphInstance.startAsyncLoad();
        }
    }

    @Configuration
    public static class AppConfig {

        @Bean
        public Graph graph() {
            // Create empty graph and start async loading
            graphInstance = new Graph(true); // true = empty constructor
            return graphInstance;
        }
        
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }
} 