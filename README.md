# Celebrity Shortest Path Finder

A modern web application that finds the shortest path between two celebrities through their shared titles, using IMDb Non-Commercial Datasets and TMDB API for photos.

## Features

- Find shortest path between any two celebrities
- Real-time photo data from TMDB API
- Fast startup with graph cache
- Free to host (uses TMDB's free API)

## How It Works

1. **Loads celebrity data** from local cast.csv.gz file (or fetches from TMDB API)
2. **Builds a graph** where celebrities are connected if they appeared in the same title
3. **Caches the graph** to disk for fast subsequent startups
4. **Finds shortest path** using bidirectional BFS algorithm

## Tech Stack

- **Backend**: Java Spring Boot 3.5.5
- **Frontend**: Next.js 15 with React
- **Data Source**: TMDB API
- **Algorithm**: Bidirectional BFS for shortest path
- **Containerization**: Docker & Docker Compose
- **Build Tool**: Maven
- **Java Version**: 21

## Project Structure

```
CelebrityShortestPathFinder/
├── backend/                 # Spring Boot API server
│   ├── src/main/java/      # Java source code
│   ├── pom.xml            # Maven configuration
│   └── Dockerfile         # Backend container
├── frontend/               # Next.js React application
│   ├── src/               # React source code
│   ├── package.json       # Node.js dependencies
│   └── Dockerfile         # Frontend container
├── docker-compose.yml     # Multi-container setup
└── README.md              # This file
```
