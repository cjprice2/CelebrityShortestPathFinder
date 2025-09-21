# Complete Setup Guide - Celebrity Shortest Path Finder (MySQL)

## ✅ What's Already Configured

Your application has been **fully converted from SQLite to MySQL**. Here's what's been updated:

### Backend Changes:
- ✅ **pom.xml**: Updated to use MySQL Connector/J instead of SQLite
- ✅ **application-database.properties**: Configured for MySQL with localhost defaults
- ✅ **application-mysql.properties**: Production-ready MySQL configuration
- ✅ **Entity classes**: Already compatible with MySQL
- ✅ **SQL queries**: Already MySQL-compatible
- ✅ **Dockerfile**: Removed SQLite database download, now uses JSON files
- ✅ **DataLoadingService**: Removed SQLite download logic, loads from JSON files
- ✅ **docker-compose.yml**: Added MySQL service with health checks

## 🚀 Quick Start (Recommended)

### Option 1: Docker (Easiest)
```bash
# 1. Set your MySQL password
export MYSQL_PASSWORD=your_secure_password

# 2. Start everything
docker-compose up -d

# 3. Check logs
docker-compose logs -f backend

# 4. Access the application
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
```

### Option 2: Local Development
```bash
# 1. Install MySQL (if not already installed)
sudo apt update
sudo apt install mysql-server

# 2. Create database and user
mysql -u root -p
# Run these SQL commands:
CREATE DATABASE celebrity_graph CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'celebrity_user'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON celebrity_graph.* TO 'celebrity_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;

# 3. Set environment variables
export DB_USERNAME=celebrity_user
export DB_PASSWORD=password

# 4. Run backend
cd backend
mvn spring-boot:run

# 5. Run frontend (in another terminal)
cd frontend
npm install
npm run dev
```

## 📁 Data Files Required

The application loads data from **ONLY ONE FILE**: `cast.csv.gz` in `backend/src/main/resources/`

**Required file:**
- `cast.csv.gz` - Contains all celebrity and title data with relationships

**File format:** CSV with columns:
- Column 1: Title ID
- Column 2: Title Name  
- Column 3: Person IDs (comma-separated list)
- Column 4: Person Names (comma-separated list)

**If you don't have this file:**
- The application will start but with an empty database
- You can add data via the API endpoints
- Or provide the `cast.csv.gz` file and restart the application

## 🔧 Environment Variables

### For Local Development:
```bash
# Database
DB_USERNAME=celebrity_user
DB_PASSWORD=password
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/celebrity_graph?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true

# Application
SPRING_PROFILES_ACTIVE=database
TMDB_API_KEY=your_tmdb_api_key_here
BFS_MAX_VISITED=3000000
BFS_MAX_QUEUE=1000000
SKIP_DATA_LOADING=false
```

### For Docker:
```bash
# MySQL
MYSQL_PASSWORD=your_secure_password
MYSQL_USER=celebrity_user

# Application
TMDB_API_KEY=your_tmdb_api_key_here
NEXT_PUBLIC_API_URL=http://backend:8080
```

## 🐳 Docker Configuration

The `docker-compose.yml` now includes:
- **MySQL 8.0** service with health checks
- **Backend** service that waits for MySQL to be ready
- **Frontend** service that depends on backend
- **Persistent volumes** for MySQL data

## 🔍 Verification Steps

1. **Check MySQL is running:**
   ```bash
   # For Docker
   docker-compose ps
   
   # For local
   sudo systemctl status mysql
   ```

2. **Check application logs:**
   ```bash
   # For Docker
   docker-compose logs backend
   
   # For local
   # Check terminal where you ran mvn spring-boot:run
   ```

3. **Test API endpoints:**
   ```bash
   # Health check
   curl http://localhost:8080/actuator/health
   
   # Database stats
   curl http://localhost:8080/api/database-stats
   
   # Search celebrities
   curl "http://localhost:8080/api/celebrities/search?query=Tom%20Hanks"
   ```

4. **Check frontend:**
   - Open http://localhost:3000
   - Try searching for celebrities
   - Test the shortest path functionality

## 🚨 Troubleshooting

### Common Issues:

1. **MySQL connection refused:**
   ```bash
   # Check if MySQL is running
   sudo systemctl status mysql
   
   # Start MySQL
   sudo systemctl start mysql
   ```

2. **Database doesn't exist:**
   - The app should create it automatically with `createDatabaseIfNotExist=true`
   - Or create manually: `CREATE DATABASE celebrity_graph;`

3. **No data in database:**
   - Check if `cast.csv.gz` exists in `backend/src/main/resources/`
   - Check application logs for data loading messages
   - Set `SKIP_DATA_LOADING=false` in environment

4. **Docker issues:**
   ```bash
   # Stop everything
   docker-compose down
   
   # Remove volumes (WARNING: deletes data)
   docker-compose down -v
   
   # Start fresh
   docker-compose up -d
   ```

## 📊 What Happens on Startup

1. **Application starts** and connects to MySQL
2. **Hibernate creates tables** automatically (if they don't exist)
3. **DataLoadingService checks** if data already exists
4. **If empty**, loads data from `cast.csv.gz` in `src/main/resources/`
5. **Creates indexes** for optimal performance
6. **Application is ready** to serve requests

## 🎯 Next Steps

1. **Get the `cast.csv.gz` file** (if you don't have it)
2. **Choose your setup method** (Docker recommended)
3. **Run the commands** above
4. **Test the application** at http://localhost:3000
5. **Enjoy your celebrity shortest path finder!** 🎉

## 📝 Summary

Your application is **100% configured for MySQL** and **no longer downloads from releases**. It now:
- Uses MySQL instead of SQLite
- Loads data from **ONLY `cast.csv.gz`** file
- Works with Docker or local development
- Has proper health checks and error handling
- Is production-ready with the right configuration

Just follow the setup steps above and you'll be running! 🚀
