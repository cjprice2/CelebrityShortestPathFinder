package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.DatabaseGraphService;
import com.example.entity.Celebrity;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CelebrityController {

    @Autowired
    private DatabaseGraphService databaseGraphService;
    
    private final RestTemplate restTemplate;
    private final String tmdbApiKey;
    private final Map<String, String> photoCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> failedPhotoLookups = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CelebrityController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.tmdbApiKey = System.getenv("TMDB_API_KEY");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "celebrity-shortest-path-finder"));
    }

    @GetMapping("/graph-status")
    public ResponseEntity<Map<String, Object>> graphStatus() {
        return ResponseEntity.ok(Map.of("building", false, "status", "ready"));
    }
    
    @GetMapping("/database-stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        // This would need to be implemented in DatabaseGraphService
        return ResponseEntity.ok(Map.of(
            "message", "Database stats endpoint - implement in DatabaseGraphService",
            "database_type", "SQLite"
        ));
    }

    @GetMapping("/shortest-path")
    public ResponseEntity<Map<String, Object>> findShortestPath(
            @RequestParam String id1,
            @RequestParam String id2,
            @RequestParam(name = "max", defaultValue = "5") int max) {
        
        List<String> results = databaseGraphService.findShortestPath(id1, id2);
        if (results.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "No path found."));
        }
        
        return ResponseEntity.ok(Map.of("results", results));
    }


    @GetMapping("/search-celebrities-graph")
    public ResponseEntity<List<Map<String, Object>>> searchCelebritiesGraph(@RequestParam String q) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        // Use database search method
        List<Celebrity> celebrities = databaseGraphService.searchCelebrities(q);
        for (Celebrity celebrity : celebrities) {
            results.add(Map.of("nconst", celebrity.getId(), "name", celebrity.getName()));
        }
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/celebrity-photo")
    public ResponseEntity<Map<String, Object>> getCelebrityPhoto(
            @RequestParam String celebrityId,
            @RequestParam(required = false) String celebrityName) {
        if (tmdbApiKey == null || tmdbApiKey.isEmpty()) {
            return ResponseEntity.ok(Map.of("photoUrl", ""));
        }

        String trimmedId = celebrityId.trim();
        
        if (failedPhotoLookups.contains(trimmedId)) {
            return ResponseEntity.ok(Map.of("photoUrl", ""));
        }
        
        String cachedPhoto = photoCache.get(trimmedId);
        if (cachedPhoto != null) {
            return ResponseEntity.ok(Map.of("photoUrl", cachedPhoto));
        }

        try {
            String tmdbPersonId = getTmdbPersonIdFromImdb(trimmedId);
            if (tmdbPersonId != null) {
                String photoUrl = getPhotoByTmdbId(tmdbPersonId);
                if (photoUrl != null) {
                    photoCache.put(trimmedId, photoUrl);
                    return ResponseEntity.ok(Map.of("photoUrl", photoUrl));
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching celebrity photo for ID " + trimmedId + ": " + e.getMessage());
        }
        
        failedPhotoLookups.add(trimmedId);
        return ResponseEntity.ok(Map.of("photoUrl", ""));
    }
    
    private String getTmdbPersonIdFromImdb(String imdbId) {
        try {
            // Use TMDB's find API to get person by IMDb ID
            String findUrl = "https://api.themoviedb.org/3/find/" + imdbId + "?api_key=" + tmdbApiKey + "&external_source=imdb_id";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> findResponse = restTemplate.getForObject(findUrl, Map.class);
            
            if (findResponse != null) {
                Object personResultsObj = findResponse.get("person_results");
                if (personResultsObj instanceof List<?> personResults && !personResults.isEmpty()) {
                    Object firstPerson = personResults.get(0);
                    if (firstPerson instanceof Map<?, ?> person) {
                        Object idObj = person.get("id");
                        if (idObj instanceof Number) {
                            return String.valueOf(((Number) idObj).intValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error finding TMDB person ID for IMDb ID " + imdbId + ": " + e.getMessage());
        }
        return null;
    }
    
    private String getPhotoByTmdbId(String tmdbPersonId) {
        try {
            String personUrl = "https://api.themoviedb.org/3/person/" + tmdbPersonId + "?api_key=" + tmdbApiKey;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> personResponse = restTemplate.getForObject(personUrl, Map.class);
            
            if (personResponse != null) {
                Object profilePathObj = personResponse.get("profile_path");
                if (profilePathObj instanceof String profilePath && !profilePath.isEmpty()) {
                    return "https://image.tmdb.org/t/p/w500" + profilePath;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching person details for TMDB ID " + tmdbPersonId + ": " + e.getMessage());
        }
        return null;
    }
    
    // Exception handler for parameter binding errors
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String error = "Invalid parameter type for '" + ex.getName() + "': " + ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", error));
    }
    
    // Exception handler for missing required parameters
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

}