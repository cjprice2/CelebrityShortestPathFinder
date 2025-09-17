package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
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

    @GetMapping("/graph-status")
    public ResponseEntity<Map<String, Object>> graphStatus() {
        boolean building = graph.isBuilding();
        String status = graph.getStatusMessage();
        return ResponseEntity.ok(Map.of("building", building, "status", status));
    }

    @GetMapping("/shortest-path")
    public ResponseEntity<Map<String, Object>> findShortestPath(
            @RequestParam String id1,
            @RequestParam String id2,
            @RequestParam(name = "max", required = false, defaultValue = "5") int max) {
        if (id1 == null || id1.isBlank() || id2 == null || id2.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please provide both celebrity IDs"));
        }
        // Use findAllShortestPaths to get multiple paths
        List<String> results = graph.findAllShortestPaths(id1, id2, Math.max(1, Math.min(5, max)));
        if (results.size() == 1 && ("No path found.".equals(results.get(0)) || results.get(0).startsWith("One or both") || results.get(0).startsWith("Invalid"))) {
            return ResponseEntity.ok(Map.of("error", results.get(0)));
        }
        
        return ResponseEntity.ok(Map.of("results", results));
    }


    // Search celebrities in the graph by name
    @GetMapping("/search-celebrities-graph")
    public ResponseEntity<List<Map<String, Object>>> searchCelebritiesGraph(@RequestParam("q") String q) {
        if (q == null || q.isBlank()) return ResponseEntity.ok(List.of());
        
        List<Map<String, Object>> results = new ArrayList<>();
        String query = q.toLowerCase().trim();
        
        // Search through all celebrities in the graph
        for (Map.Entry<String, String> entry : graph.getActorNames().entrySet()) {
            String celebrityId = entry.getKey();
            String name = entry.getValue();
            
            if (name.toLowerCase().contains(query)) {
                Map<String, Object> celebrity = new java.util.HashMap<>();
                celebrity.put("nconst", celebrityId);
                celebrity.put("name", name);
                results.add(celebrity);
                
                if (results.size() >= 20) break;
            }
        }
        
        return ResponseEntity.ok(results);
    }

    // Get celebrity photo from TMDB
    @GetMapping("/celebrity-photo")
    public ResponseEntity<Map<String, Object>> getCelebrityPhoto(
            @RequestParam(required = false) String celebrityId) {
        if (tmdbApiKey == null || tmdbApiKey.isEmpty()) {
            return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{ put("photoUrl", null); }});
        }

        // Only proceed if we have a celebrityId - no name fallback
        if (celebrityId == null || celebrityId.trim().isEmpty()) {
            return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{ put("photoUrl", null); }});
        }

        String trimmedId = celebrityId.trim();
        
        // Check if we've already tried and failed for this celebrity
        if (failedPhotoLookups.contains(trimmedId)) {
            return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{ put("photoUrl", null); }});
        }
        
        // Check cache first
        String cachedPhoto = photoCache.get(trimmedId);
        if (cachedPhoto != null) {
            return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{ put("photoUrl", cachedPhoto); }});
        }

        try {
            // Only use ID-based lookup - no name fallback
            String tmdbPersonId = getTmdbPersonIdFromImdb(trimmedId);
            if (tmdbPersonId != null) {
                String photoUrl = getPhotoByTmdbId(tmdbPersonId);
                if (photoUrl != null) {
                    photoCache.put(trimmedId, photoUrl);
                    return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{ put("photoUrl", photoUrl); }});
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching celebrity photo for ID " + trimmedId + ": " + e.getMessage());
        }
        
        // Mark as failed lookup to avoid repeated API calls
        failedPhotoLookups.add(trimmedId);
        return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{ put("photoUrl", null); }});
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
    

}