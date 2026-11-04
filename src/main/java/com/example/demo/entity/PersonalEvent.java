package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "personal_events")
public class PersonalEvent {

    @Id
    @Column(name = "token", length = 64)
    private String token;

    // Храним как TEXT, потому что это JSON-массив
    @Column(name = "events_json", columnDefinition = "TEXT")
    private String eventsJson;

    public PersonalEvent() {}

    public PersonalEvent(String token, String eventsJson) {
        this.token = token;
        this.eventsJson = eventsJson;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getEventsJson() { return eventsJson; }
    public void setEventsJson(String eventsJson) { this.eventsJson = eventsJson; }
}