# Celebrity Shortest Path Finder

A modern web application that finds the shortest path between two celebrities through their shared titles, using the TMDB API.

## Features

- Find shortest path between any two celebrities
- Real-time data from TMDB API
- Fast startup with graph cache
- Free to host (uses TMDB's free API)

## Setup

1. **Get a TMDB API Key** (free):
   - Go to https://www.themoviedb.org/settings/api
   - Create an account
   - Request an API key

2. **Set your API key**:
   ```bash
   export TMDB_API_KEY='your_api_key_here'
   ```

3. **Run the setup script**:
   ```bash
   cd backend
   ./setup.sh
   ```

4. **Start the application**:
   ```bash
   mvn spring-boot:run
   ```

## How It Works

1. **Loads celebrity data** from local cast.csv.gz file (or fetches from TMDB API)
2. **Builds a graph** where celebrities are connected if they appeared in the same title
3. **Caches the graph** to disk for fast subsequent startups
4. **Finds shortest path** using bidirectional BFS algorithm

## API Endpoints

- `GET /api/path?start=CelebrityName&end=CelebrityName` - Find shortest path between two celebrities
- `GET /api/celebrities` - List all available celebrities

## Example

```
GET /api/path?start=Tom Hanks&end=Leonardo DiCaprio
```

Response:
```
Tom Hanks <-> Leonardo DiCaprio
   Catch Me If You Can
```

## Hosting

This app is designed for efficient hosting:
- **Graph caching** - builds graph once, caches for fast restarts
- **Local data files** - uses cast.csv.gz for celebrity/title data
- **Small memory footprint** - optimized graph structure
- **Fast startup** - loads from cache when available
- **Works on Vercel (frontend) + any Java host (backend)**

### Deploy Frontend on Vercel (recommended)

1. Push this repo to GitHub.
2. In Vercel, import the `frontend/` directory as a project.
3. Set Environment Variables for the project:
   - `NEXT_PUBLIC_API_URL` = Public URL of your backend (e.g. `https://your-backend.onrender.com`)
4. Build & Deploy. Vercel will give you a production URL.

Notes:
- The frontend reads the backend URL from `NEXT_PUBLIC_API_URL` (see `frontend/next.config.mjs`).
- If you deploy the backend later, just update the Vercel environment variable and redeploy.

### Deploy Backend (choose one)

- Render/Railway/Heroku: Deploy the `backend/` as a Java 21 Spring Boot app.
  - Set env vars:
    - `TMDB_API_KEY` (required)
    - `GRAPH_CACHE_FILE` (optional, default `/tmp/graph-cache.bin`)
    - `GRAPH_RESOURCE_DIR` (optional, default `backend/src/main/resources`)
  - Expose port `8080`.
- Docker host: Build the `backend/Dockerfile` and run with the same env vars.

## Environment Variables

- `TMDB_API_KEY` - Your TMDB API key (required for photo fetching)
- `GRAPH_CACHE_FILE` - Path to graph cache file (default: `/tmp/graph-cache.bin`)
- `GRAPH_RESOURCE_DIR` - Directory containing cast.csv.gz (default: `backend/src/main/resources`)

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

## Quick Start with Docker

1. **Set your TMDB API key**:
   ```bash
   export TMDB_API_KEY='your_api_key_here'
   ```

2. **Start the application**:

   **For development** (with hot reload):
   ```bash
   docker-compose --profile dev up --build
   ```

   **For production** (optimized build):
   ```bash
   docker-compose --profile prod up --build
   ```

3. **Access the application**:
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080

## Development

### Backend Development
```bash
cd backend
mvn spring-boot:run
```

### Frontend Development
```bash
cd frontend
npm install
npm run dev
```