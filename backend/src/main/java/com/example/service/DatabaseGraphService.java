package com.example.service;

import com.example.entity.Celebrity;
import com.example.repository.CelebrityRepository;
import com.example.repository.CelebrityTitleRepository;
import com.example.repository.TitleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

import java.util.*;

@Service
public class DatabaseGraphService {
    private static final int DEFAULT_MAX_VISITED = 500_000;
    private static final int DEFAULT_MAX_QUEUE = 200_000;
    private final int maxVisited = Integer.parseInt(System.getenv().getOrDefault("BFS_MAX_VISITED", String.valueOf(DEFAULT_MAX_VISITED)));
    private final int maxQueue = Integer.parseInt(System.getenv().getOrDefault("BFS_MAX_QUEUE", String.valueOf(DEFAULT_MAX_QUEUE)));
    
    @Autowired
    private CelebrityRepository celebrityRepository;
    
    @Autowired
    private CelebrityTitleRepository celebrityTitleRepository;
    
    // Small cache for search results (60s TTL, max 1000 entries)
    private final Cache<String, List<Celebrity>> searchCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();
    
    @Autowired
    private TitleRepository titleRepository;
    
    public List<String> findShortestPath(String startQuery, String endQuery) {
        String startId = resolveCelebrityId(startQuery);
        String endId = resolveCelebrityId(endQuery);
        if (startId == null || endId == null) {
            return Collections.emptyList();
        }
        try {
            long startDegree = celebrityTitleRepository.countByCelebrityId(startId);
            long endDegree = celebrityTitleRepository.countByCelebrityId(endId);
            if (Boolean.parseBoolean(System.getenv().getOrDefault("VERBOSE_LOGS", "false"))) {
                System.out.println("BFS start: " + startId + " (deg=" + startDegree + "), end: " + endId + " (deg=" + endDegree + ")");
            }
            if (startDegree == 0 || endDegree == 0) {
                return Collections.emptyList();
            }
        } catch (Exception ignored) {}
        return bfs(startId, endId);
    }

    private String resolveCelebrityId(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();
        // If user passed an IMDb id (nm....) use it directly when present in DB
        if (q.startsWith("nm")) {
            Optional<Celebrity> found = celebrityRepository.findById(q);
            if (found.isPresent()) {
                System.out.println("Found celebrity by ID: " + q + " -> " + found.get().getName());
                return found.get().getId();
            } else {
                System.out.println("Celebrity ID not found: " + q);
                return null;
            }
        }
        // Try exact name (case-insensitive)
        Optional<Celebrity> exact = celebrityRepository.findByNameIgnoreCase(q);
        if (exact.isPresent()) {
            if (Boolean.parseBoolean(System.getenv().getOrDefault("VERBOSE_LOGS", "false"))) {
                System.out.println("Found celebrity by exact name: " + q + " -> " + exact.get().getId());
            }
            return exact.get().getId();
        }
        // Try partial name match limited (wider for better resolution)
        List<Celebrity> candidates = celebrityRepository.findTop50ByNameContainingIgnoreCase(q);
        if (!candidates.isEmpty()) {
            if (Boolean.parseBoolean(System.getenv().getOrDefault("VERBOSE_LOGS", "false"))) {
                System.out.println("Found celebrity by partial name: " + q + " -> " + candidates.get(0).getId() + " (" + candidates.get(0).getName() + ")");
            }
            return candidates.get(0).getId();
        }
        if (Boolean.parseBoolean(System.getenv().getOrDefault("VERBOSE_LOGS", "false"))) {
            System.out.println("No celebrity found for query: " + q);
        }
        return null;
    }
    
    private List<String> bfs(String startId, String endId) {
        // Check if start and end are the same
        if (startId.equals(endId)) {
            List<String> result = new ArrayList<>();
            celebrityRepository.findById(startId).ifPresent(c -> result.add(c.getName()));
            return result;
        }
        
        // Check if start and end are directly connected
        List<String> startNeighbors = celebrityTitleRepository.findConnectedCelebrityIds(startId);
        if (startNeighbors.contains(endId)) {
            String startName = celebrityRepository.findById(startId).map(Celebrity::getName).orElse(startId);
            String endName = celebrityRepository.findById(endId).map(Celebrity::getName).orElse(endId);
            
            // Find shared titles between the two celebrities
            List<String> startTitles = celebrityTitleRepository.findTitleIdsByCelebrityId(startId);
            List<String> endTitles = celebrityTitleRepository.findTitleIdsByCelebrityId(endId);
            List<String> sharedTitleIds = startTitles.stream().filter(endTitles::contains).toList();
            
            // Get title names for shared titles (limit to first 5 for display)
            List<String> sharedTitleNames = new ArrayList<>();
            for (String titleId : sharedTitleIds.subList(0, Math.min(5, sharedTitleIds.size()))) {
                titleRepository.findById(titleId).ifPresent(title -> sharedTitleNames.add(title.getName()));
            }
            
            List<String> results = new ArrayList<>();
            
            // Create multiple path results for different shared movies
            for (int i = 0; i < Math.min(5, sharedTitleIds.size()); i++) {
                String titleId = sharedTitleIds.get(i);
                String titleName = sharedTitleNames.get(i);
                
                String pathResult = startName + " -> " + endName + "\n" +
                                   "START_ID:" + startId + "\n" +
                                   "END_ID:" + endId + "\n" +
                                   "ACTOR_IDS:" + startId + "," + endId + "\n" +
                                   "MOVIE_IDS:" + titleId + "\n" +
                                   "MOVIE_TITLES:" + titleName;
                
                results.add(pathResult);
            }
            
            System.out.println("Direct connection found between " + startId + " and " + endId + ". Returning " + results.size() + " paths.");
            return results.isEmpty() ? List.of(startName + " -> " + endName + "\nSTART_ID:" + startId + "\nEND_ID:" + endId + "\nACTOR_IDS:" + startId + "," + endId) : results;
        }
        
        // Bidirectional BFS - search from both ends simultaneously
        Queue<String> forwardQueue = new LinkedList<>();
        Queue<String> backwardQueue = new LinkedList<>();
        
        Map<String, String> forwardParent = new HashMap<>();
        Map<String, String> backwardParent = new HashMap<>();
        
        Set<String> forwardVisited = new HashSet<>();
        Set<String> backwardVisited = new HashSet<>();
        
        // Initialize both searches
        forwardQueue.offer(startId);
        backwardQueue.offer(endId);
        forwardVisited.add(startId);
        backwardVisited.add(endId);
        // Start nodes have no parent
        forwardParent.put(startId, null);
        backwardParent.put(endId, null);
        
        List<String> allPaths = new ArrayList<>();
        Set<String> foundMeetingPoints = new HashSet<>();
        int shortestPathLength = -1;
        
        while (!forwardQueue.isEmpty() && !backwardQueue.isEmpty() && allPaths.size() < 5) {
            if (forwardVisited.size() + backwardVisited.size() > maxVisited) {
                System.out.println("BFS aborted: visited cap exceeded (" + (forwardVisited.size() + backwardVisited.size()) + "/" + maxVisited + ")");
                break;
            }
            
            // Expand forward search
            String meetingFromForward = expandSearch(forwardQueue, forwardVisited, forwardParent, backwardVisited, true);
            if (meetingFromForward != null && !foundMeetingPoints.contains(meetingFromForward)) {
                foundMeetingPoints.add(meetingFromForward);
                int pathLength = calculatePathLength(forwardParent, backwardParent, startId, endId, meetingFromForward);
                
                // If this is the first path found, set the shortest length
                if (shortestPathLength == -1) {
                    shortestPathLength = pathLength;
                }
                
                // Only add paths of the shortest length
                if (pathLength == shortestPathLength) {
                    List<String> path = reconstructBidirectionalPath(forwardParent, backwardParent, startId, endId, meetingFromForward);
                    allPaths.addAll(path);
                }
                // If we found a longer path, we're done (BFS guarantees we won't find shorter ones)
                else if (pathLength > shortestPathLength) {
                    break;
                }
            }
            
            // Expand backward search
            String meetingFromBackward = expandSearch(backwardQueue, backwardVisited, backwardParent, forwardVisited, false);
            if (meetingFromBackward != null && !foundMeetingPoints.contains(meetingFromBackward)) {
                foundMeetingPoints.add(meetingFromBackward);
                int pathLength = calculatePathLength(forwardParent, backwardParent, startId, endId, meetingFromBackward);
                
                // If this is the first path found, set the shortest length
                if (shortestPathLength == -1) {
                    shortestPathLength = pathLength;
                }
                
                // Only add paths of the shortest length
                if (pathLength == shortestPathLength) {
                    List<String> path = reconstructBidirectionalPath(forwardParent, backwardParent, startId, endId, meetingFromBackward);
                    allPaths.addAll(path);
                }
                // If we found a longer path, we're done
                else if (pathLength > shortestPathLength) {
                    break;
                }
            }
        }
        
        return allPaths.isEmpty() ? Collections.emptyList() : allPaths;
    }
    
    private String expandSearch(Queue<String> queue,
                                Set<String> visited,
                                Map<String, String> parent,
                                Set<String> otherVisited,
                                boolean expandingForward) {
        if (queue.isEmpty()) return null;

        String current = queue.poll();

        // Check if current node was already visited by the other search
        if (otherVisited.contains(current)) {
            return current; // meeting point found
        }

        // Explore neighbors
        List<String> neighbors = celebrityTitleRepository.findConnectedCelebrityIds(current);
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                parent.put(neighbor, current);
                
                // Check if this neighbor connects the two searches
                if (otherVisited.contains(neighbor)) {
                    return neighbor; // meeting point found
                }
                
                if (queue.size() < maxQueue) {
                    queue.offer(neighbor);
                }
            }
        }

        return null;
    }
    
    private int calculatePathLength(Map<String, String> forwardParent,
                                   Map<String, String> backwardParent,
                                   String start,
                                   String end,
                                   String meetingPoint) {
        // Count nodes from start to meeting point
        int forwardLength = 0;
        String current = meetingPoint;
        while (current != null) {
            forwardLength++;
            current = forwardParent.get(current);
        }
        
        // Count nodes from meeting point to end
        int backwardLength = 0;
        current = backwardParent.get(meetingPoint); // Skip meeting point to avoid double counting
        while (current != null) {
            backwardLength++;
            current = backwardParent.get(current);
        }
        
        return forwardLength + backwardLength;
    }
    
    private List<String> reconstructBidirectionalPath(Map<String, String> forwardParent,
                                                      Map<String, String> backwardParent,
                                                      String start,
                                                      String end,
                                                      String meetingPoint) {
        // Reconstruct path from start to meeting point using forward parents
        List<String> forwardPath = new ArrayList<>();
        String current = meetingPoint;
        while (current != null) {
            forwardPath.add(0, current);
            current = forwardParent.get(current);
        }

        // Reconstruct path from meeting point to end using backward parents
        List<String> backwardPath = new ArrayList<>();
        current = backwardParent.get(meetingPoint); // Skip meeting point to avoid duplicate
        while (current != null) {
            backwardPath.add(current);
            current = backwardParent.get(current);
        }

        // Combine paths (forward path + backward path)
        List<String> fullPath = new ArrayList<>(forwardPath);
        fullPath.addAll(backwardPath);

        // Convert IDs to names and find connecting titles
        List<String> pathNames = new ArrayList<>();
        List<String> connectingTitleIds = new ArrayList<>();
        List<String> connectingTitleNames = new ArrayList<>();
        
        for (String id : fullPath) {
            celebrityRepository.findById(id).ifPresent(celebrity ->
                pathNames.add(celebrity.getName())
            );
        }
        
        // Find titles that connect each adjacent pair of celebrities
        for (int i = 0; i < fullPath.size() - 1; i++) {
            String celeb1 = fullPath.get(i);
            String celeb2 = fullPath.get(i + 1);
            
            // Find shared titles between adjacent celebrities
            List<String> celeb1Titles = celebrityTitleRepository.findTitleIdsByCelebrityId(celeb1);
            List<String> celeb2Titles = celebrityTitleRepository.findTitleIdsByCelebrityId(celeb2);
            List<String> sharedTitles = celeb1Titles.stream().filter(celeb2Titles::contains).toList();
            
            if (!sharedTitles.isEmpty()) {
                String titleId = sharedTitles.get(0); // Take first shared title
                connectingTitleIds.add(titleId);
                titleRepository.findById(titleId).ifPresent(title -> 
                    connectingTitleNames.add(title.getName())
                );
            }
        }

        // Format as expected by frontend
        String pathResult = String.join(" -> ", pathNames) + "\n" +
                           "START_ID:" + start + "\n" +
                           "END_ID:" + end + "\n" +
                           "ACTOR_IDS:" + String.join(",", fullPath);
        
        if (!connectingTitleIds.isEmpty()) {
            pathResult += "\nMOVIE_IDS:" + String.join(",", connectingTitleIds);
        }
        if (!connectingTitleNames.isEmpty()) {
            pathResult += "\nMOVIE_TITLES:" + String.join(",", connectingTitleNames);
        }

        return List.of(pathResult);
    }
    
    
    public List<Celebrity> searchCelebrities(String query) {
        // Check cache first
        String cacheKey = query.toLowerCase().trim();
        List<Celebrity> cached = searchCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 1) Prefer prefix search heavily (LIKE 'term%')
        var page = org.springframework.data.domain.PageRequest.of(0, 10);
        List<Celebrity> collected = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        // a) Full term prefix (sorted alphabetically)
        List<Celebrity> byFullPrefix = celebrityRepository.findByNameStartingWithIgnoreCaseOrderByNameAsc(query, page).getContent();
        for (Celebrity c : byFullPrefix) if (seen.add(c.getId())) collected.add(c);
        if (collected.size() >= 10) { searchCache.put(cacheKey, collected); return collected; }

        // b) Token prefixes (try each token, longest first)
        String[] tokens = query.trim().split("\\s+");
        java.util.Arrays.sort(tokens, (a, b) -> Integer.compare(b.length(), a.length()));
        for (String t : tokens) {
            if (t.length() < 2) continue;
            List<Celebrity> pageRes = celebrityRepository.findByNameStartingWithIgnoreCaseOrderByNameAsc(t, page).getContent();
            for (Celebrity c : pageRes) {
                if (seen.add(c.getId())) collected.add(c);
                if (collected.size() >= 10) { searchCache.put(cacheKey, collected); return collected; }
            }
        }

        // Only prefix-based results are returned
        searchCache.put(cacheKey, collected);
        return collected;
    }
}
