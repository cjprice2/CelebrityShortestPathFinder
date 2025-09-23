package com.example.repository;

import com.example.entity.CelebrityTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CelebrityTitleRepository extends JpaRepository<CelebrityTitle, Long> {
    
    @Query("SELECT ct.titleId FROM CelebrityTitle ct WHERE ct.celebrityId = :celebrityId")
    List<String> findTitleIdsByCelebrityId(@Param("celebrityId") String celebrityId);
    
    @Query("SELECT ct.celebrityId FROM CelebrityTitle ct WHERE ct.titleId = :titleId")
    List<String> findCelebrityIdsByTitleId(@Param("titleId") String titleId);
    
    @Query(value = """
        SELECT DISTINCT ct2.celebrity_id 
        FROM celebrity_titles ct1 
        JOIN celebrity_titles ct2 ON ct1.title_id = ct2.title_id 
        WHERE ct1.celebrity_id = :celebrityId 
        AND ct2.celebrity_id != :celebrityId
        """, nativeQuery = true)
    List<String> findConnectedCelebrityIds(@Param("celebrityId") String celebrityId);

    // Degree of a celebrity node (number of title links)
    long countByCelebrityId(String celebrityId);
}
