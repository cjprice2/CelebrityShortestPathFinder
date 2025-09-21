package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "celebrities")
public class Celebrity {
    @Id
    @Column(name = "id")
    private String id;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "index_id")
    private Integer indexId;
    
    // Constructors
    public Celebrity() {}
    
    public Celebrity(String id, String name, Integer indexId) {
        this.id = id;
        this.name = name;
        this.indexId = indexId;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Integer getIndexId() { return indexId; }
    public void setIndexId(Integer indexId) { this.indexId = indexId; }
}
