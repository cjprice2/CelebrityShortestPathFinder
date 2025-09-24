# Celebrity Shortest Path Finder

A modern web application that finds the shortest path between two celebrities through their shared titles.

## Features

- Find shortest path between any two celebrities
- Responsive, accessible UI (mobile, tablet, desktop)
- Typeahead search with celebrity photos
- Clear selection state: green borders + checkmarks; submit gated until ready
- Clickable IMDb links for people and titles
- Fast startup with MySQL database
- Client-side result caching with friendly timeouts/errors

## Dataset (at a glance)

- 3.26M celebrities from IMDb dataset
- ~62M connections between celebrities through shared titles
- 2.04M movies/TV shows linking celebrities together
- 19.6M celebrity–title relationships (total cast/crew links)
- Average 6.0 titles per celebrity

## How It Works

1. On startup, the backend connects to a MySQL database and loads data from `cast.csv.gz`.
2. Data loading creates tables and indexes for celebrities, titles, and relationships; the DB is persisted to speed restarts.
3. Search suggestions come from the MySQL-backed index; selecting a suggestion captures IMDb IDs (`nmXXXXXXX`).
4. Shortest paths are computed via bidirectional BFS over the MySQL-backed graph.
5. Photos are fetched from TMDB (by IMDb ID) and cached in-memory; failed lookups are cached to avoid repeats.

## API Endpoints (used by the frontend)

- `GET /api/search-celebrities-graph?q=...` – search people (suggestions)
- `GET /api/celebrity-photo?celebrityId=nmXXXXXXX&celebrityName=...` – photo URL (TMDB-based)
- `GET /api/shortest-path?id1=nmXXXXXXX&id2=nmXXXXXXX&max=5` – path results
- Health/utility: `GET /api/health`, `GET /api/graph-status`

## How to Use

1. **Type celebrity name** in either search box
2. **Click search button** (🔍) to find suggestions with photos
3. **Select a celebrity** from the dropdown → green checkmark appears
4. **Repeat for second celebrity**
5. **Click "Find Shortest Path"** when both are selected → results appear

## Tech Stack

- **Backend**: Java Spring Boot 3.5.6
- **Frontend**: Next.js 15 with React
- **Database**: MySQL 8.0
- **Data Source**: IMDb Non-Commercial Datasets + TMDB for photos
- **Algorithm**: Bidirectional BFS for shortest path
- **Containerization**: Docker & Docker Compose
- **Build Tool**: Maven
- **Java Version**: 21

## Project Structure

```
CelebrityShortestPathFinder/
├── backend/                      # Spring Boot API server
│   ├── src/main/java/           # Java source code
│   ├── src/main/resources/      # Data files (cast.csv.gz, etc.)
│   ├── pom.xml                  # Maven configuration
│   └── Dockerfile               # Backend container
├── frontend/                    # Next.js app
│   ├── src/                     # React source code
│   ├── Dockerfile               # Prod container
│   └── Dockerfile.dev           # Dev container (hot reload)
├── docker-compose.yml           # Default compose (images)
├── docker-compose.dev.yml       # Dev compose (builds from source, mounts data)
└── README.md
```