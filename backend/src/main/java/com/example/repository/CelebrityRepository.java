package com.example.repository;

import com.example.entity.Celebrity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Bounded variant for better matching resolution (used in resolveCelebrityId)
    List<Celebrity> findTop50ByNameContainingIgnoreCase(String name);

    // Prefix search (index-friendly) using lower(name) LIKE 'term%' sorted alphabetically
    Page<Celebrity> findByNameStartingWithIgnoreCaseOrderByNameAsc(String prefix, Pageable pageable);

    // Trigram kNN fallback: use % (similar) and <-> (distance) on lower(name)
    @Query(value = """
      SELECT id, name
      FROM celebrities
      WHERE lower(name) % lower(:term)
      ORDER BY lower(name) <-> lower(:term)
      LIMIT :limit
      """, nativeQuery = true)
    List<Object[]> searchByTrgmKnn(@Param("term") String term, @Param("limit") int limit);

}
