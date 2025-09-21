-- Database Statistics Script for Celebrity Shortest Path Finder
-- Run with: mysql -u username -p celebrity_graph < database_stats.sql

SELECT 'Database Statistics for Celebrity Shortest Path Finder' as title;

-- Basic counts (fast)
SELECT 
    (SELECT COUNT(*) FROM celebrities) as celebrities,
    (SELECT COUNT(*) FROM titles) as titles,
    (SELECT COUNT(*) FROM celebrity_titles) as total_celebrity_title_links;

-- Count titles with multiple celebrities (this might take a moment)
SELECT 
    CONCAT('Titles with Multiple Celebrities: ', COUNT(*)) as multi_celeb_titles
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
    CONCAT('Average Titles per Celebrity: ', 
    ROUND(COUNT(*) / (SELECT COUNT(*) FROM celebrities), 1)) as avg_titles_per_celebrity
FROM celebrity_titles;

-- GitHub Release Summary (using known 62M connections)
SELECT 
    CONCAT(
        'Celebrities: ', (SELECT COUNT(*) FROM celebrities),
        ', Titles: ', (SELECT COUNT(*) FROM titles),
        ', Celebrity-Title Links: ', (SELECT COUNT(*) FROM celebrity_titles),
        ', Celebrity-Celebrity Connections: ~62M',
        ', Avg Titles/Celebrity: ', (
            SELECT ROUND(COUNT(*) / (SELECT COUNT(*) FROM celebrities), 1)
            FROM celebrity_titles
        )
    ) as github_summary;
