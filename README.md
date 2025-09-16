# Actor Shortest Path App

A simple web application that finds the shortest path between two actors through their shared movies, using the TMDB API.

## Features

- 🎬 Find shortest path between any two actors
- 🔍 Real-time data from TMDB API
- 🚀 No local data files needed
- 💰 Free to host (uses TMDB's free API)

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

1. **Fetches popular actors** from TMDB API (limited to 10,000 for free hosting)
2. **Gets movies for each actor** from their filmography
3. **Builds a graph** where actors are connected if they appeared in the same movie
4. **Finds shortest path** using bidirectional BFS algorithm

## API Endpoints

- `GET /api/path?start=ActorName&end=ActorName` - Find shortest path between two actors
- `GET /api/actors` - List all available actors

## Example

```
GET /api/path?start=Tom Hanks&end=Leonardo DiCaprio
```

Response:
```
Tom Hanks -> Leonardo DiCaprio
Tom Hanks is in Catch Me If You Can with Leonardo DiCaprio
```

## Hosting

This app is designed for free hosting:
- **No large data files** - everything fetched from API
- **Small memory footprint** - only 10,000 actors
- **Fast startup** - data loaded on demand
- **Works on Heroku, Railway, etc.**

## Environment Variables

- `TMDB_API_KEY` - Your TMDB API key (required)

## Tech Stack

- **Backend**: Java Spring Boot
- **Frontend**: React (if you add one)
- **Data Source**: TMDB API
- **Algorithm**: Bidirectional BFS for shortest path