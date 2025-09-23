package com.example.repository;

import com.example.entity.Title;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TitleRepository extends JpaRepository<Title, String> {
    
    Optional<Title> findByNameIgnoreCase(String name);
    
    @Query("SELECT t FROM Title t WHERE t.indexId = :indexId")
    Optional<Title> findByIndexId(@Param("indexId") Integer indexId);

    // Bounded variant to avoid OOMs on wide queries
    List<Title> findTop10ByNameContainingIgnoreCase(String name);
}
