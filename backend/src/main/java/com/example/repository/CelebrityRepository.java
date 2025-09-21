package com.example.repository;

import com.example.entity.Celebrity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CelebrityRepository extends JpaRepository<Celebrity, String> {
    
    Optional<Celebrity> findByNameIgnoreCase(String name);
    
    @Query("SELECT c FROM Celebrity c WHERE c.indexId = :indexId")
    Optional<Celebrity> findByIndexId(@Param("indexId") Integer indexId);

    // Fallback: allow searching by ID substring (e.g., nm123)
    List<Celebrity> findByIdContainingIgnoreCase(String id);

    // Bounded variants to avoid OOMs on wide queries (10 to match UI suggestions)
    List<Celebrity> findTop10ByNameContainingIgnoreCase(String name);
    List<Celebrity> findTop10ByIdContainingIgnoreCase(String id);

    // Additional bounded variant for better matching resolution
    List<Celebrity> findTop50ByNameContainingIgnoreCase(String name);

    // Prefix search (index-friendly). Pass pre-concatenated pattern so SQLite can use index
    @Query(value = "SELECT * FROM celebrities WHERE name LIKE :pattern COLLATE NOCASE ORDER BY name COLLATE NOCASE LIMIT 10", nativeQuery = true)
    List<Celebrity> searchByNamePrefix(@Param("pattern") String pattern);
}
