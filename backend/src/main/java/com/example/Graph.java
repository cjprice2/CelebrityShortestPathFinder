package com.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Graph implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hybrid array-based graph structures with HashMap lookups for O(1) access
    private String[] celebrityNames; // index -> celebrity name
    private String[] celebrityIds; // index -> celebrity ID (for reverse lookup)
    private String[] titleNames; // index -> title name
    private String[] titleIds; // index -> title ID (for reverse lookup)
    private int[][] adjacencyList; // celebrity index -> array of neighbor celebrity indices
    private int[] celebrityCount; // number of neighbors for each celebrity
    
    // Title-celebrity mapping for finding common titles
    private int[][] titleCelebrities; // title index -> array of celebrity indices in this title
    private int[] titleCelebrityCount; // number of celebrities for each title
    
    // Fast lookup maps for O(1) access (built after array conversion)
    private transient Map<String, Integer> celebrityIdToIndex;
    private transient Map<String, Integer> titleIdToIndex;
    
    // Temporary structures for building (cleared after build)
    private transient List<String> tempCelebrityIds = new ArrayList<>();
    private transient List<String> tempCelebrityNames = new ArrayList<>();
    private transient List<String> tempTitleIds = new ArrayList<>();
    private transient List<String> tempTitleNames = new ArrayList<>();
    private transient List<List<String>> tempCelebrityTitles = new ArrayList<>();
    private transient List<List<String>> tempNeighborSets = new ArrayList<>();

    
    // Helper method to find celebrity index by ID (O(1) HashMap lookup)
    private int findCelebrityIndex(String celebrityId) {
        if (celebrityIdToIndex == null) {
            // Fallback to linear search if maps not built yet
            if (celebrityIds == null) return -1;
            for (int i = 0; i < celebrityIds.length; i++) {
                if (celebrityIds[i] != null && celebrityIds[i].equals(celebrityId)) {
                    return i;
                }
            }
            return -1;
        }
        return celebrityIdToIndex.getOrDefault(celebrityId, -1);
    }
    
    
    // Fast search method that uses arrays directly (no duplicate data structures)
    public List<Map.Entry<String, String>> searchCelebrities(String query, int maxResults) {
        List<Map.Entry<String, String>> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();
        
        // Search directly through the arrays - no need for cached collections
        if (celebrityNames != null && celebrityIds != null) {
            for (int i = 0; i < celebrityNames.length; i++) {
                if (celebrityNames[i] != null && celebrityIds[i] != null && 
                    celebrityNames[i].toLowerCase().contains(lowerQuery)) {
                    results.add(Map.entry(celebrityIds[i], celebrityNames[i]));
                    if (results.size() >= maxResults) break;
                }
            }
        }
        
        return results;
    }
    

    public Graph() throws IOException {
        System.out.println("=== GRAPH CONSTRUCTOR START ===");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("User dir: " + System.getProperty("user.dir"));
        System.out.println("Temp dir: " + System.getProperty("java.io.tmpdir"));
        
        try {
            loadOrBuild();
            System.out.println("=== GRAPH CONSTRUCTOR END - SUCCESS ===");
        } catch (Exception e) {
            System.err.println("=== GRAPH CONSTRUCTOR END - FAILED ===");
            System.err.println("Error in Graph constructor: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void loadOrBuild() throws IOException {
        String resourceDir = System.getenv().getOrDefault("GRAPH_RESOURCE_DIR", "backend/src/main/resources");
        if (resourceDir == null || resourceDir.trim().isEmpty()) {
            resourceDir = "backend/src/main/resources";
        }
        String castFile = Paths.get(resourceDir, "cast.csv.gz").toString();
        
        // Validate paths
        if (resourceDir == null || resourceDir.trim().isEmpty()) {
            throw new IOException("Invalid resource directory: " + resourceDir);
        }
        if (castFile == null || castFile.trim().isEmpty()) {
            throw new IOException("Invalid cast file path: " + castFile);
        }
        // Use built-in cache (no environment variable needed)
        String cacheFile = "/app/graph-cache.bin.gz";
        
        System.out.println("Resource directory: " + resourceDir);
        System.out.println("Cast file: " + castFile);
        System.out.println("Cache file: " + cacheFile);
        
        // Test if paths are valid
        try {
            java.nio.file.Path testPath = Paths.get(cacheFile);
            System.out.println("Cache path test: " + testPath.toAbsolutePath() + " - VALID");
        } catch (Exception e) {
            System.err.println("Cache path test FAILED: " + e.getMessage());
        }
        
        try {
            java.nio.file.Path testPath = Paths.get(castFile);
            System.out.println("Cast path test: " + testPath.toAbsolutePath() + " - VALID");
        } catch (Exception e) {
            System.err.println("Cast path test FAILED: " + e.getMessage());
        }
        
        
        boolean canUseCache = Files.isRegularFile(Paths.get(cacheFile));
        if (canUseCache) {
            // If cache exists, use it regardless of source file timestamp
            // This allows pre-built caches to be used in production
            System.out.println("Using pre-built graph cache from: " + cacheFile);
        } else if (Files.isRegularFile(Paths.get(castFile))) {
            // Only check source file if no cache exists
            System.out.println("No cache found, will build from source data: " + castFile);
            canUseCache = false; // Force building from source
        }
        
        if (canUseCache) {
            try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(Paths.get(cacheFile))), 64 * 1024))) {
                // Load array-based data structures with larger buffer for better performance
                System.out.println("Loading graph from cache...");
                celebrityNames = (String[]) in.readObject();
                System.out.println("Loaded celebrity names: " + (celebrityNames != null ? celebrityNames.length : 0));
                celebrityIds = (String[]) in.readObject();
                System.out.println("Loaded celebrity IDs: " + (celebrityIds != null ? celebrityIds.length : 0));
                titleNames = (String[]) in.readObject();
                System.out.println("Loaded title names: " + (titleNames != null ? titleNames.length : 0));
                titleIds = (String[]) in.readObject();
                System.out.println("Loaded title IDs: " + (titleIds != null ? titleIds.length : 0));
                adjacencyList = (int[][]) in.readObject();
                System.out.println("Loaded adjacency list: " + (adjacencyList != null ? adjacencyList.length : 0));
                celebrityCount = (int[]) in.readObject();
                titleCelebrities = (int[][]) in.readObject();
                titleCelebrityCount = (int[]) in.readObject();
                // Check if cache has the new structure (titleNames should not be equal to titleIds)
                if (titleNames == null || (titleIds != null && titleNames.length == titleIds.length && java.util.Arrays.equals(titleNames, titleIds))) {
                    System.out.println("Cache has old structure (title names = IDs), rebuilding...");
                    canUseCache = false;
                } else {
                    System.out.println("Graph loaded successfully from cache");
                    buildLookupMaps(); // Build fast lookup maps after loading
                    return;
                }
            } catch (ClassNotFoundException | IOException e) {
                System.out.println("Cache invalid or corrupted, will rebuild: " + e.getMessage());
                // Cache invalid, will rebuild
            }
        }
        
        if (!Files.isRegularFile(Paths.get(castFile))) {
            System.err.println("Source data file not found: " + castFile);
            System.err.println("Initializing empty graph - will load from cache if available");
            // Initialize empty graph structures to prevent null pointer exceptions
            initializeEmptyGraph();
            return;
        }
        
        System.out.println("Building graph from source data...");
        buildFromCastData(castFile);
        buildLookupMaps(); // Build fast lookup maps after building
        saveCache(cacheFile);
    }

    private void buildFromCastData(String castFile) throws IOException {
        loadCastData(castFile);
        convertToOptimizedFormat();
    }

    private int loadCastData(String castFile) throws IOException {
        System.out.println("Loading cast data from: " + castFile);
        tempCelebrityIds.clear();
        tempCelebrityNames.clear();
        tempTitleIds.clear();
        tempCelebrityTitles.clear();
        tempNeighborSets.clear();
        
        Map<String, Integer> celebrityIdToIndex = new HashMap<>();
        Map<String, Integer> titleIdToIndex = new HashMap<>();
        Map<String, Set<String>> titleCelebrities = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get(castFile))), StandardCharsets.UTF_8), 64 * 1024)) {
            reader.readLine(); // header
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount % 100000 == 0) {
                    System.out.println("Processed " + lineCount + " lines, celebrities: " + tempCelebrityIds.size() + ", titles: " + tempTitleIds.size());
                    // Force garbage collection periodically to free memory
                    System.gc();
                }
                
                List<String> parts = parseCSVLine(line);
                if (parts.size() < 4) continue;
                
                String titleId = parts.get(0);
                String titleName = parts.size() > 1 ? parts.get(1) : titleId; // Use title name if available, fallback to ID
                String celebrityIdsStr = parts.get(2);
                String celebrityNamesStr = parts.get(3);
                
                List<String> celebrityIds = celebrityIdsStr.isEmpty() ? List.of() : Arrays.asList(celebrityIdsStr.split(","));
                List<String> names = celebrityNamesStr.isEmpty() ? List.of() : Arrays.asList(celebrityNamesStr.split(","));
                
                if (celebrityIds.size() >= 2) {
                    // Add title if not seen
                    if (!titleIdToIndex.containsKey(titleId)) {
                        titleIdToIndex.put(titleId, tempTitleIds.size());
                        tempTitleIds.add(titleId);
                        tempTitleNames.add(titleName);
                    }
                    
                    // Add celebrities and build title-celebrity relationships
                    for (int i = 0; i < celebrityIds.size(); i++) {
                        String celebrityId = celebrityIds.get(i);
                        String celebrityName = names.get(i);
                        
                        if (!celebrityIdToIndex.containsKey(celebrityId)) {
                            celebrityIdToIndex.put(celebrityId, tempCelebrityIds.size());
                            tempCelebrityIds.add(celebrityId);
                            tempCelebrityNames.add(celebrityName);
                            tempCelebrityTitles.add(new ArrayList<>());
                            tempNeighborSets.add(new ArrayList<>());
                        }
                        
                        int celebrityIndex = celebrityIdToIndex.get(celebrityId);
                        tempCelebrityTitles.get(celebrityIndex).add(titleId);
                        titleCelebrities.computeIfAbsent(titleId, k -> new HashSet<>()).add(celebrityId);
                    }
                }
            }
            System.out.println("Finished processing " + lineCount + " lines");
        }
        
        // Build neighbor relationships
        for (Set<String> celebrities : titleCelebrities.values()) {
            String[] celebrityArray = celebrities.toArray(new String[0]);
            for (int i = 0; i < celebrityArray.length; i++) {
                for (int j = i + 1; j < celebrityArray.length; j++) {
                    String a = celebrityArray[i];
                    String b = celebrityArray[j];
                    int aIndex = celebrityIdToIndex.get(a);
                    int bIndex = celebrityIdToIndex.get(b);
                    tempNeighborSets.get(aIndex).add(b);
                    tempNeighborSets.get(bIndex).add(a);
                }
            }
        }
        
        return titleCelebrities.size();
    }
    
    private void convertToOptimizedFormat() {
        int celebrityCount = tempCelebrityIds.size();
        int titleCount = tempTitleIds.size();
        
        System.out.println("Converting to optimized format: " + celebrityCount + " celebrities, " + titleCount + " titles");
        
        // Create arrays
        System.out.println("Creating arrays...");
        this.celebrityNames = tempCelebrityNames.toArray(new String[0]);
        this.celebrityIds = tempCelebrityIds.toArray(new String[0]);
        this.titleNames = tempTitleNames.toArray(new String[0]);
        this.titleIds = tempTitleIds.toArray(new String[0]);
        this.celebrityCount = new int[celebrityCount];
        this.titleCelebrityCount = new int[titleCount];
        
        // Create lookup maps for O(1) access instead of O(n) indexOf
        System.out.println("Creating lookup maps...");
        Map<String, Integer> celebrityIdToIndex = new HashMap<>();
        Map<String, Integer> titleIdToIndex = new HashMap<>();
        
        for (int i = 0; i < celebrityCount; i++) {
            celebrityIdToIndex.put(tempCelebrityIds.get(i), i);
        }
        for (int i = 0; i < titleCount; i++) {
            titleIdToIndex.put(tempTitleIds.get(i), i);
        }
        
        // Count neighbors for each celebrity
        System.out.println("Counting neighbors for celebrities...");
        for (int i = 0; i < celebrityCount; i++) {
            if (i % 100000 == 0) {
                System.out.println("Processed " + i + "/" + celebrityCount + " celebrities");
            }
            this.celebrityCount[i] = tempNeighborSets.get(i).size();
        }
        
        // Count celebrities for each title
        System.out.println("Counting celebrities for titles...");
        for (int i = 0; i < celebrityCount; i++) {
            if (i % 100000 == 0) {
                System.out.println("Processed " + i + "/" + celebrityCount + " celebrity titles");
            }
            for (String titleId : tempCelebrityTitles.get(i)) {
                Integer titleIndex = titleIdToIndex.get(titleId);
                if (titleIndex != null) {
                    this.titleCelebrityCount[titleIndex]++;
                }
            }
        }
        
        // Create adjacency list
        System.out.println("Creating adjacency list...");
        this.adjacencyList = new int[celebrityCount][];
        for (int i = 0; i < celebrityCount; i++) {
            if (i % 100000 == 0) {
                System.out.println("Processed " + i + "/" + celebrityCount + " adjacency entries");
            }
            List<String> neighbors = tempNeighborSets.get(i);
            int[] neighborArray = new int[neighbors.size()];
            for (int j = 0; j < neighbors.size(); j++) {
                Integer neighborIndex = celebrityIdToIndex.get(neighbors.get(j));
                neighborArray[j] = (neighborIndex != null) ? neighborIndex : -1;
            }
            this.adjacencyList[i] = neighborArray;
        }
        
        // Create title-celebrity mapping
        System.out.println("Creating title-celebrity mapping...");
        this.titleCelebrities = new int[titleCount][];
        for (int i = 0; i < titleCount; i++) {
            this.titleCelebrities[i] = new int[this.titleCelebrityCount[i]];
        }
        
        // Reset counts for filling
        Arrays.fill(this.titleCelebrityCount, 0);
        
        // Fill title-celebrity mapping
        System.out.println("Filling title-celebrity mapping...");
        for (int i = 0; i < celebrityCount; i++) {
            if (i % 100000 == 0) {
                System.out.println("Processed " + i + "/" + celebrityCount + " title mappings");
            }
            for (String titleId : tempCelebrityTitles.get(i)) {
                Integer titleIndex = titleIdToIndex.get(titleId);
                if (titleIndex != null) {
                    this.titleCelebrities[titleIndex][this.titleCelebrityCount[titleIndex]++] = i;
                }
            }
        }
        
        // Clear temporary data and force garbage collection
        System.out.println("Clearing temporary data...");
        tempCelebrityIds.clear();
        tempCelebrityNames.clear();
        tempTitleIds.clear();
        tempCelebrityTitles.clear();
        tempNeighborSets.clear();
        
        // Null out references to help GC
        tempCelebrityIds = null;
        tempCelebrityNames = null;
        tempTitleIds = null;
        tempCelebrityTitles = null;
        tempNeighborSets = null;
        
        // Force garbage collection to free memory immediately
        System.gc();
        
        System.out.println("Conversion completed successfully!");
    }
    
    private void buildLookupMaps() {
        System.out.println("Building fast lookup maps...");
        
        // Build celebrity ID to index map
        celebrityIdToIndex = new HashMap<>();
        if (celebrityIds != null) {
            for (int i = 0; i < celebrityIds.length; i++) {
                if (celebrityIds[i] != null) {
                    celebrityIdToIndex.put(celebrityIds[i], i);
                }
            }
        }
        
        // Build title ID to index map
        titleIdToIndex = new HashMap<>();
        if (titleIds != null) {
            for (int i = 0; i < titleIds.length; i++) {
                if (titleIds[i] != null) {
                    titleIdToIndex.put(titleIds[i], i);
                }
            }
        }
        
        System.out.println("Lookup maps built: " + celebrityIdToIndex.size() + " celebrities, " + titleIdToIndex.size() + " titles");
    }

    private void saveCache(String cacheFile) {
        try {
            System.out.println("=== SAVE CACHE DEBUG START ===");
            System.out.println("Saving cache to: " + cacheFile);
            System.out.println("Current working directory: " + System.getProperty("user.dir"));
            
            System.out.println("Step 1: Creating Path object...");
            java.nio.file.Path cachePath = Paths.get(cacheFile);
            System.out.println("Resolved cache path: " + cachePath.toAbsolutePath());
            
            System.out.println("Step 2: Getting parent directory...");
            java.nio.file.Path parent = cachePath.getParent();
            System.out.println("Parent directory: " + parent);
            
            if (parent != null && !Files.exists(parent)) {
                System.out.println("Step 3: Creating parent directory: " + parent);
                try {
                    Files.createDirectories(parent);
                    System.out.println("Parent directory created successfully");
                } catch (Exception e) {
                    System.err.println("ERROR creating parent directory: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    throw e;
                }
            } else {
                System.out.println("Parent directory already exists or is null");
            }
            
            System.out.println("Step 4: Creating output stream...");
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(cachePath)), 64 * 1024))) {
                System.out.println("Output stream created successfully");
                System.out.println("Step 5: Writing objects to cache...");
                out.writeObject(celebrityNames);
                out.writeObject(celebrityIds);
                out.writeObject(titleNames);
                out.writeObject(titleIds);
                out.writeObject(adjacencyList);
                out.writeObject(celebrityCount);
                out.writeObject(titleCelebrities);
                out.writeObject(titleCelebrityCount);
                System.out.println("All objects written successfully");
            }
            System.out.println("=== SAVE CACHE DEBUG END - SUCCESS ===");
        } catch (IOException e) {
            System.err.println("=== SAVE CACHE DEBUG END - FAILED ===");
            System.err.println("Cache save failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            // Cache save failed, continue without caching
        }
    }

    public List<String> findAllShortestPaths(String startId, String endId, int maxPaths) {
        if (startId == null || endId == null || startId.isBlank() || endId.isBlank()) return List.of("Invalid IDs.");
        
        int startIndex = findCelebrityIndex(startId);
        int endIndex = findCelebrityIndex(endId);
        
        if (startIndex == -1 || endIndex == -1) return List.of("One or both IDs do not exist.");
        if (startId.equals(endId)) return List.of(formatCelebrity(startId));

        // Bidirectional BFS
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

        while (!queueForward.isEmpty() && !queueBackward.isEmpty()) {
            // Expand forward direction
            List<String> forwardMeeting = expandLevel(queueForward, visitedForward, visitedBackward, parentForward);
            if (!forwardMeeting.isEmpty()) {
                meetingNodes.addAll(forwardMeeting);
                break;
            }
            
            // Expand backward direction
            List<String> backwardMeeting = expandLevel(queueBackward, visitedBackward, visitedForward, parentBackward);
            if (!backwardMeeting.isEmpty()) {
                meetingNodes.addAll(backwardMeeting);
                break;
            }
        }

        if (meetingNodes.isEmpty()) return List.of("No path found.");

        // Generate paths from meeting nodes
        List<String> allPaths = new ArrayList<>();
        for (String meetingNode : meetingNodes) {
            if (allPaths.size() >= maxPaths) break;
            String path = reconstructPath(parentForward, parentBackward, startId, endId, meetingNode);
            allPaths.add(path);
        }

        return allPaths;
    }

    private List<String> expandLevel(Deque<String> queue, Set<String> visited, Set<String> otherVisited, Map<String, List<String>> parent) {
        List<String> meetingNodes = new ArrayList<>();
        int levelSize = queue.size();
        
        for (int i = 0; i < levelSize; i++) {
            String current = queue.poll();

            if (otherVisited.contains(current)) {
                meetingNodes.add(current);
            }

            int currentIndex = findCelebrityIndex(current);
            if (currentIndex == -1) continue;
            
            int[] neighbors = adjacencyList[currentIndex];
            if (neighbors == null) continue;

            // Process all neighbors in one pass to minimize lookups
            for (int neighborIndex : neighbors) {
                if (neighborIndex < 0 || neighborIndex >= celebrityIds.length) continue;
                String neighbor = celebrityIds[neighborIndex]; // Direct array access
                if (neighbor == null) continue;
                if (visited.add(neighbor)) {
                    parent.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(current);
                    queue.add(neighbor);
                } else if (parent.containsKey(neighbor)) {
                    parent.get(neighbor).add(current);
                }
            }
        }
        return meetingNodes;
    }

    private String reconstructPath(Map<String, List<String>> parentForward, Map<String, List<String>> parentBackward, String startId, String endId, String meetingNode) {
        List<String> path = new ArrayList<>();

        // Build start -> meeting
        String current = meetingNode;
        while (current != null && !current.equals(startId)) {
            path.add(0, current);
            List<String> parents = parentForward.get(current);
            current = (parents != null && !parents.isEmpty()) ? parents.get(0) : null;
        }
        if (current != null) path.add(0, current);

        // Build meeting -> end
        current = meetingNode;
        List<String> backwardParents = parentBackward.get(current);
        if (backwardParents != null && !backwardParents.isEmpty()) {
            current = backwardParents.get(0);
            while (current != null && !current.equals(endId)) {
                path.add(current);
                List<String> parents = parentBackward.get(current);
                current = (parents != null && !parents.isEmpty()) ? parents.get(0) : null;
            }
            if (current != null) path.add(current);
        }

        return formatPath(path, startId, endId);
    }


    private String formatPath(List<String> path, String startId, String endId) {
        StringBuilder sb = new StringBuilder();
        List<String> celebrityIds = new ArrayList<>();
        List<String> titleIds = new ArrayList<>();
        List<String> titleNames = new ArrayList<>();
        
        for (int i = 0; i < path.size(); i++) {
            String nodeId = path.get(i);
            celebrityIds.add(nodeId);
            
            if (i < path.size() - 1) {
                String nextNodeId = path.get(i + 1);
                titleIds.add(findCommonTitleIdBetweenCelebrities(nodeId, nextNodeId));
                titleNames.add(findCommonTitleNameBetweenCelebrities(nodeId, nextNodeId));
            }
        }
        
        // Format path display
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(formatCelebrity(path.get(i)));
        }
        sb.append("\n");
        
        // Add metadata
        sb.append("START_ID:").append(startId).append("\n");
        sb.append("END_ID:").append(endId).append("\n");
        sb.append("ACTOR_IDS:").append(String.join(",", celebrityIds)).append("\n");
        sb.append("MOVIE_IDS:").append(String.join(",", titleIds)).append("\n");
        sb.append("MOVIE_TITLES:").append(String.join(",", titleNames)).append("\n");
        
        return sb.toString();
    }
    
    private String findCommonTitleNameBetweenCelebrities(String a, String b) {
        String commonTitleId = findCommonTitleIdBetweenCelebrities(a, b);
        if (commonTitleId == null || commonTitleId.equals("unknown")) {
            return "Unknown Title";
        }
        
        // Find title index by ID using O(1) lookup
        Integer titleIndex = (titleIdToIndex != null) ? titleIdToIndex.get(commonTitleId) : null;
        if (titleIndex != null && titleIndex >= 0 && titleIndex < titleNames.length) {
            return titleNames[titleIndex] != null ? titleNames[titleIndex] : commonTitleId;
        }
        
        // Fallback to linear search if maps not built yet
        for (int i = 0; i < titleIds.length; i++) {
            if (titleIds[i] != null && titleIds[i].equals(commonTitleId)) {
                return titleNames[i] != null ? titleNames[i] : commonTitleId;
            }
        }
        return "Unknown Title";
    }
    
    private String findCommonTitleIdBetweenCelebrities(String a, String b) {
        int celebrityAIndex = findCelebrityIndex(a);
        int celebrityBIndex = findCelebrityIndex(b);
        
        if (celebrityAIndex == -1 || celebrityBIndex == -1) {
            return "unknown";
        }
        
        // Find common title by checking which titles both celebrities appear in
        for (int titleIndex = 0; titleIndex < titleCelebrities.length; titleIndex++) {
            int[] celebritiesInTitle = titleCelebrities[titleIndex];
            if (celebritiesInTitle == null) continue;
            
            boolean hasA = false, hasB = false;
            for (int celebrityIndex : celebritiesInTitle) {
                if (celebrityIndex == celebrityAIndex) hasA = true;
                if (celebrityIndex == celebrityBIndex) hasB = true;
                if (hasA && hasB) {
                    return titleIds[titleIndex];
                }
            }
        }
        
        return "unknown";
    }

    private String formatCelebrity(String celebrityId) {
        int index = findCelebrityIndex(celebrityId);
        if (index != -1 && index < celebrityNames.length) {
            return celebrityNames[index];
        }
        return "Unknown";
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
    
    private void initializeEmptyGraph() {
        System.out.println("Initializing empty graph structures...");
        celebrityNames = new String[0];
        celebrityIds = new String[0];
        titleNames = new String[0];
        titleIds = new String[0];
        adjacencyList = new int[0][];
        celebrityCount = new int[0];
        titleCelebrities = new int[0][];
        titleCelebrityCount = new int[0];
        System.out.println("Empty graph initialized - ready to load from cache");
    }
}