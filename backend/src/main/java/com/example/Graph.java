package com.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class Graph implements Serializable {
    private static final long serialVersionUID = 1L;

    // Graph structures
    private Map<String, List<String>> adjacencyList = new HashMap<>(); // actor ID -> neighbor actor IDs
    private Map<String, String> actorNames = new HashMap<>(); // actor ID -> name
    private Map<String, String> movieTitles = new HashMap<>(); // movie ID -> title
    private Map<String, Set<String>> actorMovies = new HashMap<>(); // actor ID -> set of movie IDs

    // Build status
    private volatile boolean building = false;
    private volatile String statusMessage = "";

    public boolean isBuilding() { return building; }
    public String getStatusMessage() { return statusMessage; }

    public Graph() throws IOException {
        loadOrBuild();
    }

    @SuppressWarnings("unchecked")
    private void loadOrBuild() throws IOException {
        String resourceDir = System.getenv().getOrDefault("GRAPH_RESOURCE_DIR", "backend/src/main/resources");
        String castFile = Paths.get(resourceDir, "cast.csv.gz").toString();
        String cacheFile = System.getenv().getOrDefault("GRAPH_CACHE_FILE", "/tmp/graph-cache.bin");
        
        boolean canUseCache = Files.isRegularFile(Paths.get(cacheFile)) && Files.isRegularFile(Paths.get(castFile));
        if (canUseCache) {
            long cacheMtime = Files.getLastModifiedTime(Paths.get(cacheFile)).toMillis();
            long castMtime = Files.getLastModifiedTime(Paths.get(castFile)).toMillis();
            canUseCache = cacheMtime >= castMtime;
        }
        
        if (canUseCache) {
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(cacheFile))))) {
                adjacencyList = (Map<String, List<String>>) in.readObject();
                actorNames = (Map<String, String>) in.readObject();
                movieTitles = (Map<String, String>) in.readObject();
                actorMovies = (Map<String, Set<String>>) in.readObject();
                System.out.println("Loaded graph from cache");
                System.out.println("Movies: " + movieTitles.size() + 
                        ", Actors: " + actorNames.size() + 
                        ", edges: " + (adjacencyList.values().stream().mapToInt(List::size).sum() / 2));
                return;
            } catch (ClassNotFoundException e) {
                System.out.println("Cache format mismatch, rebuilding graph...");
            } catch (IOException ioe) {
                System.out.println("Failed to read cache (" + cacheFile + "): " + ioe.getMessage());
            }
        }
        
        if (!Files.isRegularFile(Paths.get(castFile))) {
            throw new IOException("Cast file not found: " + castFile);
        }
        
        buildFromCastData(castFile);
        saveCache(cacheFile);
    }

    private void buildFromCastData(String castFile) throws IOException {
        System.out.println("Building graph from cast data...");
        building = true;
        statusMessage = "Loading cast data";
        int movieCount = loadCastData(castFile);
        trimAdjacencyLists();
        System.out.println("Finished building graph. Movies: " + movieCount + 
                ", Actors: " + actorNames.size() + 
                ", edges: " + (adjacencyList.values().stream().mapToInt(List::size).sum() / 2));
        statusMessage = "Done";
        building = false;
    }

    private int loadCastData(String castFile) throws IOException {
        Map<String, Set<String>> neighborSets = new HashMap<>();
        Map<String, Set<String>> movieActors = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get(castFile))), StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            long count = 0L;
            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCSVLine(line);
                if (parts.size() < 4) continue;
                
                String movieId = parts.get(0);
                String movieTitle = parts.get(1);
                String actorIdsStr = parts.get(2);
                String actorNamesStr = parts.get(3);
                
                List<String> actorIds = actorIdsStr.isEmpty() ? List.of() : Arrays.asList(actorIdsStr.split(","));
                List<String> names = actorNamesStr.isEmpty() ? List.of() : Arrays.asList(actorNamesStr.split(","));
                
                movieTitles.put(movieId, movieTitle);
                
                if (actorIds.size() >= 2) {
                    for (int i = 0; i < actorIds.size(); i++) {
                        String actorId = actorIds.get(i);
                        String actorName = names.get(i);
                        
                        actorNames.put(actorId, actorName);
                        movieActors.computeIfAbsent(movieId, k -> new HashSet<>()).add(actorId);
                        actorMovies.computeIfAbsent(actorId, k -> new HashSet<>()).add(movieId);
                    }
                }
                
                if ((++count % 50000) == 0) System.out.println("Processed movies: " + count);
            }
        }
        
        System.out.println("Building edges from " + movieActors.size() + " movies...");
        long edgeCount = 0;
        int movieCount = 0;
        for (Set<String> actors : movieActors.values()) {
            String[] actorArray = actors.toArray(new String[0]);
            
            for (int i = 0; i < actorArray.length; i++) {
                for (int j = i + 1; j < actorArray.length; j++) {
                    String a = actorArray[i];
                    String b = actorArray[j];
                    neighborSets.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                    neighborSets.computeIfAbsent(b, k -> new HashSet<>()).add(a);
                    edgeCount++;
                }
            }
            
            if (++movieCount % 50000 == 0) {
                System.out.println("Processed " + movieCount + " movies, built " + edgeCount + " edges...");
            }
        }
        
        for (Map.Entry<String, Set<String>> e : neighborSets.entrySet()) {
            adjacencyList.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        
        return movieActors.size();
    }

    private void saveCache(String cacheFile) {
        try {
            java.nio.file.Path cachePath = Paths.get(cacheFile);
            java.nio.file.Path parent = cachePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.exists(cachePath) && Files.isDirectory(cachePath)) {
                System.out.println("Cache path points to a directory, skipping save: " + cacheFile);
                return;
            }
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(cachePath)))) {
                out.writeObject(adjacencyList);
                out.writeObject(actorNames);
                out.writeObject(movieTitles);
                out.writeObject(actorMovies);
                System.out.println("Saved graph cache");
            }
        } catch (IOException e) {
            System.out.println("Failed to save cache: " + e.getMessage());
        }
    }

    private void trimAdjacencyLists() {
        for (List<String> neighbors : adjacencyList.values()) {
            if (neighbors instanceof ArrayList<?> arr) {
                arr.trimToSize();
            }
        }
    }

    // --- NEW: BIDIRECTIONAL BFS IMPLEMENTATION ---
    public String findShortestPath(String startId, String endId) {
        if (startId == null || endId == null || startId.isBlank() || endId.isBlank()) return "Invalid IDs.";
        if (!adjacencyList.containsKey(startId) || !adjacencyList.containsKey(endId)) return "One or both IDs do not exist.";
        if (startId.equals(endId)) return formatActor(startId);

        // Data structures for the forward search (from startId)
        Deque<String> queueForward = new ArrayDeque<>();
        Map<String, String> parentForward = new HashMap<>();
        Set<String> visitedForward = new HashSet<>();

        // Data structures for the backward search (from endId)
        Deque<String> queueBackward = new ArrayDeque<>();
        Map<String, String> parentBackward = new HashMap<>();
        Set<String> visitedBackward = new HashSet<>();

        queueForward.add(startId);
        visitedForward.add(startId);
        parentForward.put(startId, null);

        queueBackward.add(endId);
        visitedBackward.add(endId);
        parentBackward.put(endId, null);

        while (!queueForward.isEmpty() && !queueBackward.isEmpty()) {
            // Always expand the smaller frontier first for efficiency
            boolean forwardFirst = queueForward.size() <= queueBackward.size();
            String meetingNode;

            if (forwardFirst) {
                meetingNode = expandLevel(queueForward, visitedForward, visitedBackward, parentForward);
                if (meetingNode != null) {
                    return reconstructBidirectionalPath(parentForward, parentBackward, startId, endId, meetingNode);
                }

                meetingNode = expandLevel(queueBackward, visitedBackward, visitedForward, parentBackward);
                if (meetingNode != null) {
                    return reconstructBidirectionalPath(parentForward, parentBackward, startId, endId, meetingNode);
                }
            } else {
                meetingNode = expandLevel(queueBackward, visitedBackward, visitedForward, parentBackward);
                if (meetingNode != null) {
                    return reconstructBidirectionalPath(parentForward, parentBackward, startId, endId, meetingNode);
                }

                meetingNode = expandLevel(queueForward, visitedForward, visitedBackward, parentForward);
                if (meetingNode != null) {
                    return reconstructBidirectionalPath(parentForward, parentBackward, startId, endId, meetingNode);
                }
            }
        }

        return "No path found.";
    }

    private String expandLevel(Deque<String> queue, Set<String> visited, Set<String> otherVisited, Map<String, String> parent) {
        int levelSize = queue.size();
        for (int i = 0; i < levelSize; i++) {
            String current = queue.poll();

            // Check for intersection
            if (otherVisited.contains(current)) {
                return current; // Found the meeting point
            }

            List<String> neighbors = adjacencyList.get(current);
            if (neighbors == null) continue;

            for (String neighbor : neighbors) {
                if (visited.add(neighbor)) { // True if the neighbor was not already visited
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        return null; // No meeting point found at this level
    }

    private String reconstructBidirectionalPath(Map<String, String> parentForward, Map<String, String> parentBackward, String startId, String endId, String meetingNode) {
        Deque<String> deque = new ArrayDeque<>();

        // Build start -> meeting by prepending while walking parents from the meeting to start
        String current = meetingNode;
        while (current != null) {
            deque.addFirst(current);
            current = parentForward.get(current);
        }

        // Then append meeting -> end by following backward parents from the node after meeting
        current = parentBackward.get(meetingNode);
        while (current != null) {
            deque.addLast(current);
            current = parentBackward.get(current);
        }

        List<String> path = new ArrayList<>(deque);
        return formatPathFromNodeList(path, startId, endId);
    }
    // --- END OF BIDIRECTIONAL BFS IMPLEMENTATION ---


    public List<String> findAllShortestPaths(String startId, String endId, int maxPaths) {
        if (startId == null || endId == null || startId.isBlank() || endId.isBlank()) return List.of("Invalid IDs.");
        if (!adjacencyList.containsKey(startId) || !adjacencyList.containsKey(endId)) return List.of("One or both IDs do not exist.");
        if (startId.equals(endId)) return List.of(formatActor(startId));

        // First BFS to compute distances and predecessor lists
        Map<String, Integer> distance = new HashMap<>();
        Map<String, List<String>> predecessors = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();

        distance.put(startId, 0);
        queue.add(startId);

        int foundDistance = -1;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currDist = distance.get(current);
            if (foundDistance != -1 && currDist + 1 > foundDistance) break;

            List<String> neighbors = adjacencyList.get(current);
            if (neighbors == null) continue;
            for (String neighbor : neighbors) {
                int nextDist = currDist + 1;
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, nextDist);
                    predecessors.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(current);
                    queue.add(neighbor);
                } else if (distance.get(neighbor) == nextDist) {
                    predecessors.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(current);
                }
                if (neighbor.equals(endId)) {
                    foundDistance = nextDist;
                }
            }
        }

        if (foundDistance == -1) return List.of("No path found.");

        // Enumerate paths using DFS on predecessor graph
        List<List<String>> rawPaths = new ArrayList<>();
        Deque<String> currentPath = new ArrayDeque<>();
        currentPath.push(endId);

        findPathsDfs(startId, endId, predecessors, currentPath, rawPaths, maxPaths);

        List<String> formatted = new ArrayList<>();
        for (List<String> path : rawPaths) {
            formatted.add(formatPathFromNodeList(path, startId, endId));
        }
        return formatted.isEmpty() ? List.of("No path found.") : formatted;
    }

    private void findPathsDfs(String startId, String currentNode, Map<String, List<String>> predecessors, Deque<String> currentPath, List<List<String>> allPaths, int maxPaths) {
        if (allPaths.size() >= maxPaths) return;

        if (currentNode.equals(startId)) {
            List<String> path = new ArrayList<>(currentPath);
            Collections.reverse(path);
            allPaths.add(path);
            return;
        }

        List<String> preds = predecessors.getOrDefault(currentNode, List.of());
        for (String pred : preds) {
            currentPath.push(pred);
            findPathsDfs(startId, pred, predecessors, currentPath, allPaths, maxPaths);
            currentPath.pop();
            if (allPaths.size() >= maxPaths) return;
        }
    }

    private String formatPathFromNodeList(List<String> path, String startId, String endId) {
        StringBuilder sb = new StringBuilder();
        // Explicit endpoints for frontend normalization
        sb.append("START_ID:").append(startId).append("\n");
        sb.append("END_ID:").append(endId).append("\n");
        // First line: actor names joined with arrows (frontend displays these)
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(formatActor(path.get(i)));
        }
        sb.append("\n");

        // Collect movie IDs and titles for each hop
        List<String> movieIds = new ArrayList<>();
        List<String> movieTitlesOut = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            String a = path.get(i);
            String b = path.get(i + 1);
            movieIds.add(findCommonMovieIdBetweenActors(a, b));
            movieTitlesOut.add(findCommonTitleBetweenActors(a, b));
        }

        // Actor IDs line
        sb.append("ACTOR_IDS:");
        for (String actorId : path) {
            sb.append(actorId).append(",");
        }
        sb.append("\n");

        // Movie IDs line
        sb.append("MOVIE_IDS:");
        for (String movieId : movieIds) {
            sb.append(movieId).append(",");
        }
        sb.append("\n");

        // Movie titles line for frontend (no sentence parsing)
        sb.append("MOVIE_TITLES:");
        for (String title : movieTitlesOut) {
            sb.append(title).append(",");
        }

        return sb.toString();
    }

    private String findCommonTitleBetweenActors(String a, String b) {
        Set<String> ta = actorMovies.getOrDefault(a, Set.of());
        Set<String> tb = actorMovies.getOrDefault(b, Set.of());
        for (String t : ta) if (tb.contains(t)) return movieTitles.getOrDefault(t, t);
        return "Unknown Title";
    }
    
    private String findCommonMovieIdBetweenActors(String a, String b) {
        Set<String> ta = actorMovies.getOrDefault(a, Set.of());
        Set<String> tb = actorMovies.getOrDefault(b, Set.of());
        for (String t : ta) if (tb.contains(t)) return t;
        return "unknown";
    }

    private String formatActor(String actorId) {
        return actorNames.getOrDefault(actorId, "Unknown");
    }

    public Map<String, String> getActorNames() {
        return new HashMap<>(actorNames);
    }
    
    private List<String> parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') { // Handle escaped quote
                    current.append('"');
                    i++; 
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        result.add(current.toString());
        return result;
    }
}