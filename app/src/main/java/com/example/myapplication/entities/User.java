package com.example.myapplication.entities;

public class User {
    private String uid;
    private String email;
    private String name;
    private String phone;
    private String role; // "dealer" or "agent" (admin is web-only)
    private String dealerId; // null for dealers, dealer's uid for agents
    private boolean active;
    private java.util.Date createdAt;
    private java.util.Date updatedAt;

    // Default constructor
    public User() {}

    // Constructor for new users
    public User(String uid, String email, String name, String phone, String role) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.active = true;
        this.createdAt = new java.util.Date();
        this.updatedAt = new java.util.Date();
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDealerId() { return dealerId; }
    public void setDealerId(String dealerId) { this.dealerId = dealerId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public java.util.Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.util.Date createdAt) { this.createdAt = createdAt; }

    public java.util.Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.util.Date updatedAt) { this.updatedAt = updatedAt; }

    // Helper methods
    public boolean isDealer() {
        return "dealer".equals(role);
    }

    public boolean isAgent() {
        return "agent".equals(role);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}

