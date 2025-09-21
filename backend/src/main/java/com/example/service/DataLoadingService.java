package com.example.service;

import com.example.entity.Celebrity;
import com.example.entity.Title;
import com.example.entity.CelebrityTitle;
import com.example.repository.CelebrityRepository;
import com.example.repository.TitleRepository;
import com.example.repository.CelebrityTitleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Service
public class DataLoadingService {
    
    @Autowired
    private CelebrityRepository celebrityRepository;
    
    @Autowired
    private TitleRepository titleRepository;
    
    @Autowired
    private CelebrityTitleRepository celebrityTitleRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public void loadDataFromFilesIfNeeded() {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("SKIP_DATA_LOADING", "false"))) {
            System.out.println("Data loading skipped via SKIP_DATA_LOADING=true");
            return;
        }
        
        try {
            // Check if data already exists
            long celebrityCount = celebrityRepository.count();
            if (celebrityCount > 0) {
                System.out.println("Data already exists in database (" + celebrityCount + " celebrities). Skipping data loading.");
                return;
            }
            
            System.out.println("Database is empty. Starting data loading...");
            loadDataFromFiles();
        } catch (Exception e) {
            // If count() fails (tables don't exist), Hibernate will create them automatically
            System.out.println("Tables not found. Hibernate will create them automatically. Starting data loading...");
            loadDataFromFiles();
        }
    }

    public void loadDataFromFiles() {
        String resourceDir = System.getenv().getOrDefault("GRAPH_RESOURCE_DIR", "backend/src/main/resources");
        String castFile = Paths.get(resourceDir, "cast.csv.gz").toString();
        // Names and titles are expected to be included in cast.csv.gz; no external TSV fallback
        
        System.out.println("Loading data from: " + castFile);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get(castFile))), StandardCharsets.UTF_8), 16 * 1024)) {
            
            String header = reader.readLine(); // header
            if (header == null) {
                System.err.println("cast.csv.gz is empty or unreadable");
                return;
            }
            
            // Log the header for debugging
            System.out.println("CSV Header: " + header);
            String[] headerCols = splitSmart(header);
            System.out.println("Detected " + headerCols.length + " columns: " + java.util.Arrays.toString(headerCols));
            String line;
            int lineCount = 0;
            int batchSize = Integer.parseInt(System.getenv().getOrDefault("BATCH_SIZE", "1000"));
            List<CelebrityTitle> batch = new ArrayList<>();
            
            Map<String, Celebrity> celebrityMap = new HashMap<>();
            Map<String, Title> titleMap = new HashMap<>();
            int celebrityIndex = 0;
            int titleIndex = 0;
            
            // Read first non-empty row to verify all required columns present
            String firstDataRow = null;
            while ((firstDataRow = reader.readLine()) != null && firstDataRow.trim().isEmpty()) {
                // skip empty lines
            }
            if (firstDataRow == null) {
                System.err.println("cast.csv.gz has no data rows");
                return;
            }
            String[] firstCols = splitSmart(firstDataRow);
            if (firstCols.length < 4) {
                System.err.println("cast.csv.gz first data row missing columns (need 4): got " + firstCols.length);
                System.err.println("First data row: " + firstDataRow);
                System.err.println("Parsed columns: " + java.util.Arrays.toString(firstCols));
                return;
            }
            
            // Log first data row for debugging
            System.out.println("First data row: " + firstDataRow);
            System.out.println("Parsed columns: " + java.util.Arrays.toString(firstCols));
            // Rewind handling: process the first row immediately
            {
                String titleId = firstCols[0].trim();
                String titleName = firstCols[1].trim();
                String[] personIds = splitList(firstCols[2]);
                String[] personNames = splitList(firstCols[3]);
                if (!titleMap.containsKey(titleId)) {
                    Title title = new Title(titleId, titleName.isEmpty() ? titleId : titleName, titleIndex++);
                    titleMap.put(titleId, title);
                }
                int pairs = Math.min(personIds.length, personNames.length);
                for (int i = 0; i < pairs; i++) {
                    String celebrityId = personIds[i];
                    String celebrityName = personNames[i];
                    if (!celebrityMap.containsKey(celebrityId)) {
                        Celebrity celebrity = new Celebrity(celebrityId, (celebrityName.isEmpty() ? celebrityId : celebrityName), celebrityIndex++);
                        celebrityMap.put(celebrityId, celebrity);
                    }
                    batch.add(new CelebrityTitle(celebrityId, titleId));
                }
                lineCount++;
            }

            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }
                
                String[] cols = splitSmart(line);
                if (cols.length < 4) {
                    System.err.println("Skipping malformed row " + lineCount + " (need 4 columns, got " + cols.length + "): " + line);
                    continue;
                }
                String titleId = cols[0].trim();
                String titleName = cols[1].trim();
                String[] personIds = splitList(cols[2]);
                String[] personNames = splitList(cols[3]);

                if (!titleMap.containsKey(titleId)) {
                    Title title = new Title(titleId, titleName.isEmpty() ? titleId : titleName, titleIndex++);
                    titleMap.put(titleId, title);
                }

                int pairs = Math.min(personIds.length, personNames.length);
                for (int i = 0; i < pairs; i++) {
                    String celebrityId = personIds[i];
                    String celebrityName = personNames[i];
                    if (!celebrityMap.containsKey(celebrityId)) {
                        Celebrity celebrity = new Celebrity(celebrityId, (celebrityName.isEmpty() ? celebrityId : celebrityName), celebrityIndex++);
                        celebrityMap.put(celebrityId, celebrity);
                    }
                    batch.add(new CelebrityTitle(celebrityId, titleId));

                    if (batch.size() >= batchSize) {
                        processBatch(celebrityMap, titleMap, batch);
                        batch.clear();
                        System.out.println("Processed " + lineCount + " lines");
                    }
                }
            }
            
            // Process remaining batch
            if (!batch.isEmpty()) {
                processBatch(celebrityMap, titleMap, batch);
            }
            
            System.out.println("Data loading completed. Processed " + lineCount + " lines");
            System.out.println("Loaded " + celebrityMap.size() + " unique celebrities and " + titleMap.size() + " unique titles");
            
        } catch (IOException e) {
            System.err.println("Error loading data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void processBatch(Map<String, Celebrity> celebrityMap, Map<String, Title> titleMap, List<CelebrityTitle> batch) {
        // Save celebrities and titles
        celebrityRepository.saveAll(celebrityMap.values());
        titleRepository.saveAll(titleMap.values());
        
        // Save relationships
        celebrityTitleRepository.saveAll(batch);
        
        // Clear maps to free memory
        celebrityMap.clear();
        titleMap.clear();
    }
    
    private String[] splitSmart(String line) {
        // Prefer tab if present
        if (line.indexOf('\t') >= 0) {
            return line.split("\t", -1);
        }
        // CSV with quotes handling
        java.util.List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                // toggle or escape
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(trimQuotes(cur.toString()));
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(trimQuotes(cur.toString()));
        return out.toArray(new String[0]);
    }
    
    private String[] splitList(String field) {
        String f = trimQuotes(field);
        if (f.isEmpty() || f.equals("null")) return new String[0];
        
        // Handle different separators (comma, semicolon, pipe)
        String[] parts;
        if (f.contains(";")) {
            parts = f.split(";");
        } else if (f.contains("|")) {
            parts = f.split("\\|");
        } else {
            parts = f.split(",");
        }
        
        // Clean up each part
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
            // Remove empty entries
            if (parts[i].isEmpty()) {
                parts[i] = null;
            }
        }
        
        // Filter out null entries
        return java.util.Arrays.stream(parts)
                .filter(java.util.Objects::nonNull)
                .toArray(String[]::new);
    }
    
    private String trimQuotes(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }
    
}
