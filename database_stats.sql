-- Database Statistics Script for Celebrity Shortest Path Finder
-- Run with: sqlite3 celebrity_graph.db < database_stats.sql

.headers on
.mode column

SELECT 'Database Statistics for Celebrity Shortest Path Finder' as title;

-- Basic counts (fast)
SELECT 
    (SELECT COUNT(*) FROM celebrities) as celebrities,
    (SELECT COUNT(*) FROM titles) as titles,
    (SELECT COUNT(*) FROM celebrity_titles) as total_celebrity_title_links;

-- Count titles with multiple celebrities (this might take a moment)
SELECT 
    'Titles with Multiple Celebrities: ' || COUNT(*)
FROM (
    SELECT title_id
    FROM celebrity_titles 
    GROUP BY title_id
    HAVING COUNT(*) > 1
) multi_celeb_titles;

-- Use your known 62M figure for now
SELECT 
    'Celebrity-to-Celebrity Connections: ~62M (from data processing)' as connections;

-- Average titles per celebrity (simpler metric)
SELECT 
    'Average Titles per Celebrity: ' || 
    ROUND(CAST(COUNT(*) AS FLOAT) / (SELECT COUNT(*) FROM celebrities), 1)
FROM celebrity_titles;

-- GitHub Release Summary (using known 62M connections)
SELECT 
    'Celebrities: ' || (SELECT COUNT(*) FROM celebrities) ||
    ', Titles: ' || (SELECT COUNT(*) FROM titles) ||
    ', Celebrity-Title Links: ' || (SELECT COUNT(*) FROM celebrity_titles) ||
    ', Celebrity-Celebrity Connections: ~62M' ||
    ', Avg Titles/Celebrity: ' || (
        SELECT ROUND(CAST(COUNT(*) AS FLOAT) / (SELECT COUNT(*) FROM celebrities), 1)
        FROM celebrity_titles
    ) as github_summary;
