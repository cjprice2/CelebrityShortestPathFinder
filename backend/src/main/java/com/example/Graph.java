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
    private Map<String, List<String>> adjacencyList = new HashMap<>(); // celebrity ID -> neighbor celebrity IDs
    private Map<String, String> celebrityNames = new HashMap<>(); // celebrity ID -> name
    private Map<String, String> titleNames = new HashMap<>(); // title ID -> title name
    private Map<String, Set<String>> celebrityTitles = new HashMap<>(); // celebrity ID -> set of title IDs

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
                celebrityNames = (Map<String, String>) in.readObject();
                titleNames = (Map<String, String>) in.readObject();
                celebrityTitles = (Map<String, Set<String>>) in.readObject();
                System.out.println("Loaded graph from cache");
                System.out.println("Titles: " + titleNames.size() + 
                        ", Celebrities: " + celebrityNames.size() + 
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
        int titleCount = loadCastData(castFile);
        trimAdjacencyLists();
        System.out.println("Finished building graph. Titles: " + titleCount + 
                ", Celebrities: " + celebrityNames.size() + 
                ", edges: " + (adjacencyList.values().stream().mapToInt(List::size).sum() / 2));
        statusMessage = "Done";
        building = false;
    }

    private int loadCastData(String castFile) throws IOException {
        Map<String, Set<String>> neighborSets = new HashMap<>();
        Map<String, Set<String>> titleCelebrities = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get(castFile))), StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            long count = 0L;
            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCSVLine(line);
                if (parts.size() < 4) continue;
                
                String titleId = parts.get(0);
                String titleName = parts.get(1);
                String celebrityIdsStr = parts.get(2);
                String celebrityNamesStr = parts.get(3);
                
                List<String> celebrityIds = celebrityIdsStr.isEmpty() ? List.of() : Arrays.asList(celebrityIdsStr.split(","));
                List<String> names = celebrityNamesStr.isEmpty() ? List.of() : Arrays.asList(celebrityNamesStr.split(","));
                
                titleNames.put(titleId, titleName);
                
                if (celebrityIds.size() >= 2) {
                    for (int i = 0; i < celebrityIds.size(); i++) {
                        String celebrityId = celebrityIds.get(i);
                        String celebrityName = names.get(i);
                        
                        celebrityNames.put(celebrityId, celebrityName);
                        titleCelebrities.computeIfAbsent(titleId, k -> new HashSet<>()).add(celebrityId);
                        celebrityTitles.computeIfAbsent(celebrityId, k -> new HashSet<>()).add(titleId);
                    }
                }
                
                if ((++count % 50000) == 0) System.out.println("Processed titles: " + count);
            }
        }
        
        System.out.println("Building edges from " + titleCelebrities.size() + " titles...");
        long edgeCount = 0;
        int titleCount = 0;
        for (Set<String> celebrities : titleCelebrities.values()) {
            String[] celebrityArray = celebrities.toArray(new String[0]);
            
            for (int i = 0; i < celebrityArray.length; i++) {
                for (int j = i + 1; j < celebrityArray.length; j++) {
                    String a = celebrityArray[i];
                    String b = celebrityArray[j];
                    neighborSets.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                    neighborSets.computeIfAbsent(b, k -> new HashSet<>()).add(a);
                    edgeCount++;
                }
            }
            
            if (++titleCount % 50000 == 0) {
                System.out.println("Processed " + titleCount + " titles, built " + edgeCount + " edges...");
            }
        }
        
        for (Map.Entry<String, Set<String>> e : neighborSets.entrySet()) {
            adjacencyList.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        
        return titleCelebrities.size();
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
                out.writeObject(celebrityNames);
                out.writeObject(titleNames);
                out.writeObject(celebrityTitles);
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

    public String findShortestPath(String startId, String endId) {
        List<String> paths = findAllShortestPaths(startId, endId, 1);
        return paths.isEmpty() ? "No path found." : paths.get(0);
    }

    public List<String> findAllShortestPaths(String startId, String endId, int maxPaths) {
        if (startId == null || endId == null || startId.isBlank() || endId.isBlank()) return List.of("Invalid IDs.");
        if (!adjacencyList.containsKey(startId) || !adjacencyList.containsKey(endId)) return List.of("One or both IDs do not exist.");
        if (startId.equals(endId)) return List.of(formatCelebrity(startId));

        // Use bidirectional BFS to find all shortest paths
        Map<String, List<String>> parentForward = new HashMap<>();
        Map<String, List<String>> parentBackward = new HashMap<>();
        Set<String> visitedForward = new HashSet<>();
        Set<String> visitedBackward = new HashSet<>();
        Deque<String> queueForward = new ArrayDeque<>();
        Deque<String> queueBackward = new ArrayDeque<>();

        queueForward.add(startId);
        visitedForward.add(startId);
        parentForward.put(startId, new ArrayList<>());

        queueBackward.add(endId);
        visitedBackward.add(endId);
        parentBackward.put(endId, new ArrayList<>());

        List<String> meetingNodes = new ArrayList<>();
        int shortestDistance = -1;
        int level = 0;

        while (!queueForward.isEmpty() && !queueBackward.isEmpty()) {
            level++;
            
            // Expand forward direction
            List<String> forwardMeeting = expandLevelBidirectional(queueForward, visitedForward, visitedBackward, parentForward);
            if (!forwardMeeting.isEmpty() && shortestDistance == -1) {
                shortestDistance = level;
                meetingNodes.addAll(forwardMeeting);
            }
            
            // Expand backward direction
            List<String> backwardMeeting = expandLevelBidirectional(queueBackward, visitedBackward, visitedForward, parentBackward);
            if (!backwardMeeting.isEmpty() && shortestDistance == -1) {
                shortestDistance = level;
                meetingNodes.addAll(backwardMeeting);
            }

            // If we found meeting nodes, stop
            if (shortestDistance != -1) {
                break;
            }
        }

        if (meetingNodes.isEmpty()) return List.of("No path found.");

        // Generate all paths from meeting nodes
        List<String> allPaths = new ArrayList<>();
        for (String meetingNode : meetingNodes) {
            if (allPaths.size() >= maxPaths) break;
            String path = reconstructBidirectionalPathMultiple(parentForward, parentBackward, startId, endId, meetingNode);
            allPaths.add(path);
        }

        return allPaths;
    }

    private List<String> expandLevelBidirectional(Deque<String> queue, Set<String> visited, Set<String> otherVisited, Map<String, List<String>> parent) {
        List<String> meetingNodes = new ArrayList<>();
        int levelSize = queue.size();
        
        for (int i = 0; i < levelSize; i++) {
            String current = queue.poll();

            // Check for intersection
            if (otherVisited.contains(current)) {
                meetingNodes.add(current);
            }

            List<String> neighbors = adjacencyList.get(current);
            if (neighbors == null) continue;

            for (String neighbor : neighbors) {
                if (visited.add(neighbor)) { // True if the neighbor was not already visited
                    parent.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(current);
                    queue.add(neighbor);
                } else if (parent.containsKey(neighbor)) {
                    // If already visited, check if this is another way to reach it at the same level
                    parent.get(neighbor).add(current);
                }
            }
        }
        return meetingNodes;
    }

    private String reconstructBidirectionalPathMultiple(Map<String, List<String>> parentForward, Map<String, List<String>> parentBackward, String startId, String endId, String meetingNode) {
        List<String> path = new ArrayList<>();

        // Build start -> meeting by walking parents from the meeting to start
        String current = meetingNode;
        while (current != null && !current.equals(startId)) {
            path.add(0, current); // Add to beginning
            List<String> parents = parentForward.get(current);
            current = (parents != null && !parents.isEmpty()) ? parents.get(0) : null;
        }
        if (current != null) path.add(0, current); // Add start node

        // Then append meeting -> end by following backward parents from the node after meeting
        current = meetingNode;
        List<String> backwardParents = parentBackward.get(current);
        if (backwardParents != null && !backwardParents.isEmpty()) {
            current = backwardParents.get(0);
            while (current != null && !current.equals(endId)) {
                path.add(current);
                List<String> parents = parentBackward.get(current);
                current = (parents != null && !parents.isEmpty()) ? parents.get(0) : null;
            }
            if (current != null) path.add(current); // Add end node
        }

        return formatPathFromNodeList(path, startId, endId);
    }


    private String formatPathFromNodeList(List<String> path, String startId, String endId) {
        StringBuilder sb = new StringBuilder();
        
        // Extract celebrity IDs and title IDs
        List<String> celebrityIds = new ArrayList<>();
        List<String> titleIds = new ArrayList<>();
        List<String> titleNames = new ArrayList<>();
        
        for (int i = 0; i < path.size(); i++) {
            String nodeId = path.get(i);
            celebrityIds.add(nodeId);
            
            if (i < path.size() - 1) {
                String nextNodeId = path.get(i + 1);
                String commonTitleId = findCommonTitleIdBetweenCelebrities(nodeId, nextNodeId);
                String commonTitleName = findCommonTitleNameBetweenCelebrities(nodeId, nextNodeId);
                titleIds.add(commonTitleId != null ? commonTitleId : "unknown");
                titleNames.add(commonTitleName != null ? commonTitleName : "Unknown Title");
            }
        }
        
        // Format the path for display (this should be the first non-metadata line)
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(formatCelebrity(path.get(i)));
        }
        sb.append("\n");
        
        // Explicit endpoints for frontend normalization
        sb.append("START_ID:").append(startId).append("\n");
        sb.append("END_ID:").append(endId).append("\n");
        
        // API format uses legacy naming for backward compatibility
        sb.append("ACTOR_IDS:").append(String.join(",", celebrityIds)).append("\n");
        sb.append("MOVIE_IDS:").append(String.join(",", titleIds)).append("\n");
        sb.append("MOVIE_TITLES:").append(String.join(",", titleNames)).append("\n");
        
        return sb.toString();
    }
    
    private String findCommonTitleNameBetweenCelebrities(String a, String b) {
        Set<String> ta = celebrityTitles.getOrDefault(a, Set.of());
        Set<String> tb = celebrityTitles.getOrDefault(b, Set.of());
        for (String t : ta) if (tb.contains(t)) return titleNames.getOrDefault(t, "Unknown Title");
        return "Unknown Title";
    }
    
    private String findCommonTitleIdBetweenCelebrities(String a, String b) {
        Set<String> ta = celebrityTitles.getOrDefault(a, Set.of());
        Set<String> tb = celebrityTitles.getOrDefault(b, Set.of());
        for (String t : ta) if (tb.contains(t)) return t;
        return "unknown";
    }

    private String formatCelebrity(String celebrityId) {
        return celebrityNames.getOrDefault(celebrityId, "Unknown");
    }

    public Map<String, String> getActorNames() {
        return new HashMap<>(celebrityNames);
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