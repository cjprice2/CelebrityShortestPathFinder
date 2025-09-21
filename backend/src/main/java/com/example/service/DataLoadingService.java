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
import javax.sql.DataSource;

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
    
    @Autowired
    private DataSource dataSource;
    
    
    public void loadDataFromFilesIfNeeded() {
        try {
            if (Boolean.parseBoolean(System.getenv().getOrDefault("SKIP_DATA_LOADING", "false"))) {
                System.out.println("Data loading skipped via SKIP_DATA_LOADING=true");
                return;
            }
            
            // First, try to download pre-built database if it doesn't exist
            downloadDatabaseIfNeeded();
            
            // Check if data already exists
            long celebrityCount = celebrityRepository.count();
            if (celebrityCount > 0) {
                System.out.println("Data already exists in database (" + celebrityCount + " celebrities). Skipping data loading.");
                return;
            }
            
            System.out.println("Database is empty. Starting data loading...");
            createTablesIfNeeded(); // This already creates all necessary indexes
            loadDataFromFiles();
        } catch (Exception e) {
            // If count() fails (tables don't exist), create tables and proceed with data loading
            System.out.println("Tables not found. Creating tables and starting data loading...");
            createTablesIfNeeded(); // This already creates all necessary indexes
            loadDataFromFiles();
        }
    }
    
    private void downloadDatabaseIfNeeded() {
        try {
            String dbPath = System.getenv().getOrDefault("DB_PATH", "/app/data/celebrity_graph.db");
            java.io.File dbFile = new java.io.File(dbPath);
            
            // Check if pre-built database exists and has data
            if (dbFile.exists() && dbFile.length() > 0) {
                try {
                    long count = celebrityRepository.count();
                    if (count > 0) {
                        System.out.println("Pre-built database already exists with " + count + " celebrities at: " + dbPath);
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("Existing database file appears corrupted: " + e.getMessage());
                }
            }
            
            // If no pre-built database, download from GitHub releases
            String dbUrl = System.getenv().getOrDefault("DATABASE_DOWNLOAD_URL", 
                "https://github.com/cjprice2/CelebrityShortestPathFinder/releases/download/v1.0.0/celebrity_graph.db.gz");
            System.out.println("No pre-built database found, downloading from: " + dbUrl);
            
            java.io.File tempGzFile = new java.io.File(dbPath + ".gz");
            java.net.URI uri = java.net.URI.create(dbUrl);
            try (java.io.InputStream in = uri.toURL().openStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(tempGzFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    if (totalBytes % (10 * 1024 * 1024) == 0) { // Log every 10MB
                        System.out.println("Downloaded: " + (totalBytes / 1024 / 1024) + "MB");
                    }
                }
                
                System.out.println("Database download completed: " + (totalBytes / 1024 / 1024) + "MB");
            }
            
            // Extract the gzipped file
            extractGzipFile(tempGzFile, dbFile);
            tempGzFile.delete(); // Clean up temp file
        } catch (Exception e) {
            System.out.println("Failed to download database: " + e.getMessage());
            System.out.println("Will attempt to load from local files instead...");
        }
    }
    
    private void extractGzipFile(java.io.File gzFile, java.io.File outputFile) throws Exception {
        System.out.println("Extracting " + gzFile.getName() + " (" + (gzFile.length() / 1024 / 1024) + "MB) to " + outputFile.getPath());
        
        try (java.io.FileInputStream fis = new java.io.FileInputStream(gzFile);
             java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(fis);
             java.io.FileOutputStream out = new java.io.FileOutputStream(outputFile)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                if (totalBytes % (50 * 1024 * 1024) == 0) { // Log every 50MB
                    System.out.println("Extracted: " + (totalBytes / 1024 / 1024) + "MB");
                }
            }
            
            System.out.println("Extraction completed: " + (totalBytes / 1024 / 1024) + "MB extracted to " + outputFile.getPath());
        }
    }
    
    public void createTablesIfNeeded() {
        try (
            var conn = dataSource.getConnection();
            var stmt = conn.createStatement()
        ) {
            // Ensure autocommit so DDL executes immediately
            boolean prevAuto = conn.getAutoCommit();
            try {
                conn.setAutoCommit(true);
                // Create base tables
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS celebrities (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255), index_id INTEGER)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS titles (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255), index_id INTEGER)");
                // Match entity mapping: id surrogate key + columns
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS celebrity_titles (id INTEGER PRIMARY KEY AUTOINCREMENT, celebrity_id VARCHAR(255), title_id VARCHAR(255))");
                // Helpful indexes
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ct_celebrity ON celebrity_titles(celebrity_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ct_title ON celebrity_titles(title_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_celeb_name ON celebrities(name)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_title_name ON titles(name)");
            } finally {
                try { conn.setAutoCommit(prevAuto); } catch (Exception ignore) {}
            }
            System.out.println("Tables created successfully");
        } catch (Exception e) {
            System.out.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
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
                return;
            }
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
                String[] cols = splitSmart(line);
                if (cols.length < 4) {
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
        
        // Release managed entities to reduce memory pressure between batches
        try { entityManager.clear(); } catch (Exception ignore) {}
        
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
        if (f.isEmpty()) return new String[0];
        String[] parts = f.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
    
    private String trimQuotes(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }
    
}
