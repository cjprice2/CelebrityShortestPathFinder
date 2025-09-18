package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CelebrityController {

    private final Graph graph;
    private final RestTemplate restTemplate;
    private final String tmdbApiKey;
    private final Map<String, String> photoCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> failedPhotoLookups = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public CelebrityController(Graph graph, RestTemplate restTemplate) {
        this.graph = graph;
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

    @GetMapping("/shortest-path")
    public ResponseEntity<Map<String, Object>> findShortestPath(
            @RequestParam String id1,
            @RequestParam String id2,
            @RequestParam(name = "max", defaultValue = "5") int max) {
        
        List<String> results = graph.findAllShortestPaths(id1, id2, Math.max(1, Math.min(5, max)));
        if (results.size() == 1 && ("No path found.".equals(results.get(0)) || results.get(0).startsWith("One or both") || results.get(0).startsWith("Invalid"))) {
            return ResponseEntity.ok(Map.of("error", results.get(0)));
        }
        
        return ResponseEntity.ok(Map.of("results", results));
    }


    @GetMapping("/search-celebrities-graph")
    public ResponseEntity<List<Map<String, Object>>> searchCelebritiesGraph(@RequestParam String q) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        // Use optimized search method
        for (Map.Entry<String, String> entry : graph.searchCelebrities(q, 20)) {
            results.add(Map.of("nconst", entry.getKey(), "name", entry.getValue()));
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