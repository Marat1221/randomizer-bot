package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "shared_events")
public class SharedEvent {

    @Id
    @Column(name = "share_id", length = 10)
    private String shareId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 10)
    private String emoji;

    @Column(nullable = false, length = 20)
    private String date; // Сохраняем как строку "2026-06-01"

    @Column(length = 20)
    private String color;

    public SharedEvent() {}

    public SharedEvent(String shareId, String title, String emoji, String date, String color) {
        this.shareId = shareId;
        this.title = title;
        this.emoji = emoji;
        this.date = date;
        this.color = color;
    }

    // Геттеры и сеттеры
    public String getShareId() { return shareId; }
    public void setShareId(String shareId) { this.shareId = shareId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}