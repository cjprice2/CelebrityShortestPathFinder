package com.example.service;

import com.example.repository.CelebrityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

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
    private JdbcTemplate jdbcTemplate;
    
    @Transactional(readOnly = true)
    public void loadDataFromFilesIfNeeded() {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("SKIP_DATA_LOADING", "false"))) {
            System.out.println("Data loading skipped via SKIP_DATA_LOADING=true");
            return;
        }
        
        try {
            // Check if data already exists
            long celebrityCount = celebrityRepository.count();
            if (celebrityCount > 0) {
                System.out.println("✅ Data already exists in database (" + celebrityCount + " celebrities). Skipping data loading.");
                return;
            }
        } catch (Exception e) {
            System.out.println("❌ Cannot connect to database or database doesn't exist: " + e.getMessage());
            System.out.println("Please ensure the 'celebrity_graph' database exists on your PostgreSQL server.");
            return;
        }
        
        try {
            
            System.out.println("Database is empty. Checking for pre-built database...");
            
            // Try to restore from pre-built database
            if (restoreFromPreBuiltDatabase()) {
                System.out.println("Successfully restored from pre-built database!");
                return;
            }
            
            System.out.println("❌ No pre-built database found!");
            System.out.println("🔄 Falling back to CSV loading for database creation...");
            loadDataFromCSV();
            return;
        } catch (Exception e) {
            // If count() fails (tables don't exist), Hibernate will create them automatically
            System.out.println("Tables not found. Hibernate will create them automatically.");
            System.out.println("Checking for pre-built database...");
            
            if (restoreFromPreBuiltDatabase()) {
                System.out.println("Successfully restored from pre-built database!");
                return;
            }
            
            System.out.println("❌ No pre-built database found!");
            System.out.println("🔄 Falling back to CSV loading for database creation...");
            loadDataFromCSV();
        }
    }

    @Transactional
    public void loadDataFromCSV() {
        String resourceDir = System.getenv().getOrDefault("GRAPH_RESOURCE_DIR", "/home/colin/projects/CelebrityShortestPathFinder/backend/src/main/resources");
        String castFile = Paths.get(resourceDir, "cast.csv.gz").toString();
        
        System.out.println("📂 Loading data from: " + castFile);
        // Ensure schema/indexes needed for ON CONFLICT are present to avoid SQL grammar errors
        try {
            // Enable pg_trgm extension for trigram searches
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            
            // Celebrity-Title relationship indexes
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_celebrity_titles_pair ON celebrity_titles(celebrity_id, title_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_celebrity_titles_celebrity_id ON celebrity_titles(celebrity_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_celebrity_titles_title_id ON celebrity_titles(title_id)");
            
            // Optimized celebrity search indexes for fast prefix and kNN searches
            // 1. Pattern index for prefix searches (LIKE 'term%') - fastest for prefix matching
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_celebrities_name_lower_pattern ON celebrities USING btree (lower(name) text_pattern_ops)");
            
            // 2. GiST trigram index for kNN similarity searches (ORDER BY distance)
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_celebrities_name_lower_gist_trgm ON celebrities USING gist (lower(name) gist_trgm_ops)");
            
            // 3. GIN trigram index for general trigram searches (fallback)
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_celebrities_name_lower_trgm ON celebrities USING gin (lower(name) gin_trgm_ops)");
            
            // 4. ID search index (for exact ID lookups)
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_celebrities_id_trgm ON celebrities USING gin (id gin_trgm_ops)");
            
            // Update table statistics for optimal query planning
            jdbcTemplate.execute("ANALYZE celebrities");
            
            System.out.println("✅ Database indexes created successfully for optimized search performance");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Some indexes may not have been created: " + e.getMessage());
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get(castFile))), StandardCharsets.UTF_8), 16 * 1024)) {
            
            String header = reader.readLine(); // header
            if (header == null) {
                System.err.println("❌ cast.csv.gz is empty or unreadable");
                return;
            }
            
            System.out.println("📋 CSV Header: " + header);
            String[] headerCols = splitSmart(header);
            System.out.println("📊 Detected " + headerCols.length + " columns: " + java.util.Arrays.toString(headerCols));
            String line;
            int lineCount = 0;
            int batchSize = Integer.parseInt(System.getenv().getOrDefault("BATCH_SIZE", "100000")); // Batch size (tune for your DB/WAL limits)
            
            // Use StringBuilder for bulk SQL - MUCH faster than JPA
            StringBuilder celebritySQL = new StringBuilder("INSERT INTO celebrities (id, name, index_id) VALUES ");
            StringBuilder titleSQL = new StringBuilder("INSERT INTO titles (id, name, index_id) VALUES ");
            StringBuilder relationSQL = new StringBuilder("INSERT INTO celebrity_titles (celebrity_id, title_id) VALUES ");
            
            Set<String> seenCelebrities = new HashSet<>();
            Set<String> seenTitles = new HashSet<>();
            
            int celebrityIndex = 0;
            int titleIndex = 0;
            int relationCount = 0;
            
            long startTime = System.currentTimeMillis();
            try { jdbcTemplate.execute("SET synchronous_commit = off"); } catch (Exception ignored) {}
            
            // Process data line by line
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }
                
                String[] cols = splitSmart(line);
                if (cols.length < 4) {
                    System.err.println("⚠️ Skipping malformed row " + lineCount + " (need 4 columns, got " + cols.length + "): " + line);
                    continue;
                }
                String titleId = cols[0].trim();
                String titleName = escapeSQL(cols[1].trim());
                String[] personIds = splitList(cols[2]);
                String[] personNames = splitList(cols[3]);

                // Add title if not seen
                if (!seenTitles.contains(titleId)) {
                    if (titleIndex > 0) titleSQL.append(",");
                    titleSQL.append("('").append(titleId).append("','").append(titleName.isEmpty() ? titleId : titleName).append("',").append(titleIndex++).append(")");
                    seenTitles.add(titleId);
                }

                // Add celebrities and relationships
                int pairs = Math.min(personIds.length, personNames.length);
                for (int i = 0; i < pairs; i++) {
                    String celebrityId = personIds[i].trim();
                    String celebrityName = escapeSQL(personNames[i].trim());
                    
                    // Add celebrity if not seen
                    if (!seenCelebrities.contains(celebrityId)) {
                        if (celebrityIndex > 0) celebritySQL.append(",");
                        celebritySQL.append("('").append(celebrityId).append("','").append(celebrityName.isEmpty() ? celebrityId : celebrityName).append("',").append(celebrityIndex++).append(")");
                        seenCelebrities.add(celebrityId);
                    }
                    
                    // Add relationship
                    if (relationCount > 0) relationSQL.append(",");
                    relationSQL.append("('").append(celebrityId).append("','").append(titleId).append("')");
                    relationCount++;
                }

                // Execute bulk inserts every batchSize lines
                if (lineCount % batchSize == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println("🚀 BULK PROCESSING " + lineCount + " lines | " + seenCelebrities.size() + " celebrities | " + seenTitles.size() + " titles | " + (elapsed/1000) + "s");
                    executeBulkSQL(celebritySQL, titleSQL, relationSQL, celebrityIndex, titleIndex, relationCount);
                    
                    // Reset builders for next batch
                    celebritySQL = new StringBuilder("INSERT INTO celebrities (id, name, index_id) VALUES ");
                    titleSQL = new StringBuilder("INSERT INTO titles (id, name, index_id) VALUES ");
                    relationSQL = new StringBuilder("INSERT INTO celebrity_titles (celebrity_id, title_id) VALUES ");
                    celebrityIndex = titleIndex = relationCount = 0;
                }
            }
            
            // Process remaining data
            if (celebrityIndex > 0 || titleIndex > 0 || relationCount > 0) {
                System.out.println("🎉 Processing final batch...");
                executeBulkSQL(celebritySQL, titleSQL, relationSQL, celebrityIndex, titleIndex, relationCount);
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("🎉 ULTRA-FAST DATA LOADING COMPLETED! 🚀");
            System.out.println("📊 Processed " + lineCount + " lines in " + (totalTime/1000) + " seconds");
            System.out.println("📊 Final counts: " + seenCelebrities.size() + " unique celebrities, " + seenTitles.size() + " unique titles");
            System.out.println("⚡ Speed: " + (lineCount / Math.max(1, totalTime/1000)) + " lines/second");
            // Update planner statistics
            try {
                jdbcTemplate.execute("ANALYZE celebrities");
                jdbcTemplate.execute("ANALYZE titles");
                jdbcTemplate.execute("ANALYZE celebrity_titles");
            } catch (Exception ignored) {}
            
        } catch (IOException e) {
            System.err.println("❌ Error loading data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void executeBulkSQL(StringBuilder celebritySQL, StringBuilder titleSQL, StringBuilder relationSQL, int celebrityCount, int titleCount, int relationCount) {
        try {
            // Execute bulk inserts - break into smaller chunks to avoid PostgreSQL limits
            if (celebrityCount > 0) {
                String sql = celebritySQL.toString() + " ON CONFLICT (id) DO NOTHING";
                executeSQLInChunks(sql, "celebrities");
            }
            
            if (titleCount > 0) {
                String sql = titleSQL.toString() + " ON CONFLICT (id) DO NOTHING";
                executeSQLInChunks(sql, "titles");
            }
            
            if (relationCount > 0) {
                String sql = relationSQL.toString() + " ON CONFLICT (celebrity_id, title_id) DO NOTHING";
                executeSQLInChunks(sql, "relationships");
            }
            
        } catch (Exception e) {
            // Avoid printing full SQL statements (which can include massive nm/tt lists)
            System.err.println("❌ Bulk SQL Error: " + e.getClass().getSimpleName());
            // Continue processing - don't stop on individual batch errors
        }
    }
    
    private void executeSQLInChunks(String sql, String type) {
        try {
            // For very large SQL statements, PostgreSQL might reject them
            // Execute directly for now, but could split if needed
            jdbcTemplate.update(sql);
        } catch (Exception e) {
            // Keep logs concise to prevent dumping entire SQL with values
            System.err.println("❌ Failed to insert " + type + ": " + e.getClass().getSimpleName());
        }
    }
    
    private String escapeSQL(String input) {
        if (input == null) return "";
        return input.replace("'", "''"); // Escape single quotes for SQL
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
    
    private boolean restoreFromPreBuiltDatabase() {
        String resourceDir = System.getenv().getOrDefault("GRAPH_RESOURCE_DIR", "backend/src/main/resources");
        String dbFile = Paths.get(resourceDir, "celebrity_graph.db.gz").toString();
        
        System.out.println("Checking for pre-built database at: " + dbFile);
        
        if (!Files.exists(Paths.get(dbFile))) {
            System.out.println("Pre-built database file not found: " + dbFile);
            return false;
        }
        
        try {
            System.out.println("Found pre-built database. Restoring...");
            
            // Get database connection details from environment
            String dbHost = System.getenv().getOrDefault("DB_HOST", "postgres");
            String dbPort = System.getenv().getOrDefault("DB_PORT", "5432");
            String dbName = System.getenv().getOrDefault("DB_NAME", "celebrity_graph");
            String dbUsername = System.getenv().getOrDefault("DB_USERNAME", "postgres");
            String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "password");
            
            // Decompress the database file
            String tempDbFile = "/tmp/celebrity_graph.db";
            try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(Paths.get(dbFile)));
                 FileOutputStream fileOut = new FileOutputStream(tempDbFile)) {
                
                byte[] buffer = new byte[8192];
                int len;
                while ((len = gzipIn.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, len);
                }
            }
            
            // Restore using psql command
            ProcessBuilder pb = new ProcessBuilder(
                "psql", 
                "-h", dbHost,
                "-p", dbPort,
                "-U", dbUsername,
                "-d", dbName,
                "-f", tempDbFile
            );
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            // Clean up temp file
            Files.deleteIfExists(Paths.get(tempDbFile));
            
            if (exitCode == 0) {
                System.out.println("Database restored successfully from pre-built file!");
                return true;
            } else {
                System.err.println("Failed to restore database. Exit code: " + exitCode);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error restoring from pre-built database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // CSV parsing methods removed - only using pre-built database now
    
}
