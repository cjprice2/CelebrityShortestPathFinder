package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.example.service.DataLoadingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class CelebrityShortestPathFinderApplication implements CommandLineRunner {

    @Autowired
    private DataLoadingService dataLoadingService;

    public static void main(String[] args) {
        System.setProperty("server.address", "0.0.0.0");
        System.setProperty("server.port", "8080");
        System.setProperty("spring.profiles.active", "database");
        SpringApplication.run(CelebrityShortestPathFinderApplication.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        // Ensure data directory and empty database file exist before Spring tries to connect
        String dbPath = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:sqlite:/app/data/celebrity_graph.db");
        if (dbPath.startsWith("jdbc:sqlite:")) {
            String filePath = dbPath.substring("jdbc:sqlite:".length());
            java.io.File dbFile = new java.io.File(filePath);
            java.io.File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
                System.out.println("Created data directory: " + parentDir.getAbsolutePath());
            }
            if (!dbFile.exists()) {
                dbFile.createNewFile();
                System.out.println("Created empty database file: " + dbFile.getAbsolutePath());
            }
        }
        System.out.println("Application started. Data loading will begin after context initialization...");
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        System.out.println("Application context is ready. Checking if data loading is needed...");
        dataLoadingService.loadDataFromFilesIfNeeded();
        System.out.println("Data loading check completed!");
    }

    @Configuration
    public static class AppConfig {
        
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }
} 