package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "celebrity_titles")
public class CelebrityTitle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "celebrity_id")
    private String celebrityId;
    
    @Column(name = "title_id")
    private String titleId;
    
    // Constructors
    public CelebrityTitle() {}
    
    public CelebrityTitle(String celebrityId, String titleId) {
        this.celebrityId = celebrityId;
        this.titleId = titleId;
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCelebrityId() { return celebrityId; }
    public void setCelebrityId(String celebrityId) { this.celebrityId = celebrityId; }
    
    public String getTitleId() { return titleId; }
    public void setTitleId(String titleId) { this.titleId = titleId; }
}
