package com.example.demo.entity;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeedbackService {
    public static class ChatMessage {
        public final String from; public final String text; public final long time;
        public ChatMessage(String from, String text, long time) { this.from = from; this.text = text; this.time = time; }
    }
    public static class Session {
        public final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
        public volatile boolean closed = false;
    }
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session getOrCreate(String sessionId) { return sessions.computeIfAbsent(sessionId, k -> new Session()); }
    public Session get(String sessionId) { return sessions.get(sessionId); }
    public void addUserMessage(String sessionId, String text) { getOrCreate(sessionId).messages.add(new ChatMessage("user", text, System.currentTimeMillis())); }
    public void addAdminMessage(String sessionId, String text) { getOrCreate(sessionId).messages.add(new ChatMessage("admin", text, System.currentTimeMillis())); }
    public void close(String sessionId) { Session s = sessions.get(sessionId); if (s != null) s.closed = true; }

    // Для админки
    public Map<String, Session> getAllSessions() { return sessions; }
}