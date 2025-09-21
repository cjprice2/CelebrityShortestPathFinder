# MySQL Setup Guide for Celebrity Shortest Path Finder

## Overview
This application has been converted from SQLite to MySQL. Follow these steps to get everything running.

## Prerequisites
- Java 21+
- Maven 3.6+
- MySQL 8.0+ (or Docker)
- Node.js 18+ (for frontend)

## Option 1: Local MySQL Installation

### 1. Install MySQL
**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install mysql-server
sudo mysql_secure_installation
```

**macOS (with Homebrew):**
```bash
brew install mysql
brew services start mysql
```

**Windows:**
Download and install from [MySQL official website](https://dev.mysql.com/downloads/mysql/)

### 2. Create Database and User
```sql
-- Connect to MySQL as root
mysql -u root -p

-- Create database
CREATE DATABASE celebrity_graph CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user (replace 'password' with your desired password)
CREATE USER 'celebrity_user'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON celebrity_graph.* TO 'celebrity_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 3. Configure Environment Variables
Create a `.env` file in the project root:
```bash
# Database Configuration
DB_USERNAME=celebrity_user
DB_PASSWORD=password
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/celebrity_graph?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true

# Application Configuration
TMDB_API_KEY=your_tmdb_api_key_here
SPRING_PROFILES_ACTIVE=database
BFS_MAX_VISITED=3000000
BFS_MAX_QUEUE=1000000
SKIP_DATA_LOADING=false
```

## Option 2: Docker with MySQL

### 1. Update docker-compose.yml
The docker-compose.yml has been updated to include MySQL service.

### 2. Run with Docker Compose
```bash
# Start MySQL and the application
docker-compose up -d

# Check logs
docker-compose logs -f backend
```

## Running the Application

### 1. Backend (Spring Boot)
```bash
cd backend

# Install dependencies
mvn clean install

# Run the application
mvn spring-boot:run

# Or with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

### 2. Frontend (Next.js)
```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm run dev
```

## Database Migration

The application will automatically:
1. Create tables if they don't exist (via Hibernate DDL)
2. Load data from the provided JSON files
3. Create necessary indexes

## Configuration Files

### application-database.properties
- **Default database**: MySQL (localhost:3306)
- **Auto-create database**: Yes (`createDatabaseIfNotExist=true`)
- **DDL mode**: `update` (creates/updates schema automatically)
- **Connection pool**: Optimized for MySQL

### application-mysql.properties
- **Production-ready**: For DigitalOcean or other cloud providers
- **SSL enabled**: For secure connections
- **Environment variables**: Uses `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

## Troubleshooting

### Common Issues

1. **Connection refused to MySQL**
   - Ensure MySQL is running: `sudo systemctl status mysql`
   - Check if port 3306 is open: `netstat -tlnp | grep 3306`

2. **Authentication failed**
   - Verify username/password in `.env` file
   - Check MySQL user privileges: `SHOW GRANTS FOR 'celebrity_user'@'localhost';`

3. **Database doesn't exist**
   - The app should create it automatically with `createDatabaseIfNotExist=true`
   - Manually create: `CREATE DATABASE celebrity_graph;`

4. **Data loading issues**
   - Check if JSON data files exist in `backend/src/main/resources/`
   - Verify file permissions
   - Check application logs for specific errors

### Performance Tuning

1. **MySQL Configuration** (`/etc/mysql/mysql.conf.d/mysqld.cnf`):
```ini
[mysqld]
innodb_buffer_pool_size = 1G
innodb_log_file_size = 256M
max_connections = 200
query_cache_size = 64M
```

2. **Application JVM Settings**:
```bash
export JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

## Data Loading

The application loads data from these files (if they exist):
- `celebrity_data.json.gz` - Celebrity information
- `title_data.json.gz` - Movie/TV show information  
- `celebrity_title_data.json.gz` - Relationships between celebrities and titles

If these files don't exist, the application will:
1. Log a warning
2. Continue with empty database
3. Be ready to receive data via API

## Production Deployment

### Environment Variables for Production
```bash
# Database (use your production MySQL instance)
DB_HOST=your-mysql-host
DB_PORT=3306
DB_NAME=celebrity_graph
DB_USERNAME=your-username
DB_PASSWORD=your-secure-password

# Application
SPRING_PROFILES_ACTIVE=mysql
TMDB_API_KEY=your-tmdb-api-key
BFS_MAX_VISITED=5000000
BFS_MAX_QUEUE=2000000
```

### Docker Production Build
```bash
# Build backend
cd backend
docker build -t celebrity-backend:mysql .

# Run with production MySQL
docker run -d \
  --name celebrity-backend \
  -p 8080:8080 \
  -e DB_HOST=your-mysql-host \
  -e DB_USERNAME=your-username \
  -e DB_PASSWORD=your-password \
  -e SPRING_PROFILES_ACTIVE=mysql \
  celebrity-backend:mysql
```

## Monitoring

### Database Monitoring
```sql
-- Check table sizes
SELECT 
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'Size (MB)'
FROM information_schema.tables 
WHERE table_schema = 'celebrity_graph'
ORDER BY (data_length + index_length) DESC;

-- Check connection status
SHOW PROCESSLIST;

-- Check slow queries
SHOW VARIABLES LIKE 'slow_query_log';
```

### Application Monitoring
- Health check: `GET http://localhost:8080/actuator/health`
- Database stats: `GET http://localhost:8080/api/database-stats`
- Metrics: `GET http://localhost:8080/actuator/metrics`

## Backup and Recovery

### Backup Database
```bash
mysqldump -u celebrity_user -p celebrity_graph > celebrity_graph_backup.sql
```

### Restore Database
```bash
mysql -u celebrity_user -p celebrity_graph < celebrity_graph_backup.sql
```

## Support

If you encounter issues:
1. Check the application logs
2. Verify MySQL connection and permissions
3. Ensure all environment variables are set correctly
4. Check that data files exist and are readable
