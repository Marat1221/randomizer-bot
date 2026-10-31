package com.example.demo.entity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.demo")
public class Main {

    public static void main(String... args) {
        SpringApplication.run(Main.class, args);
    }

    @RestController
    @RequestMapping("/api")
    public static class CounterController {

        @Autowired private FeedbackService feedbackService;
        @Autowired private TelegramNotifier notifier;

        // ===== ПУБЛИЧНЫЕ ЭНДПОИНТЫ =====
        @GetMapping("/value")
        public long getServerTime() { return System.currentTimeMillis(); }

        @GetMapping("/events")
        public List<EventDto> getEvents() {
            return List.of(
                    new EventDto("newyear", "До Нового Года", "2027-01-01T00:00:00+03:00"),
                    new EventDto("ramadan", "До Рамадана", "2027-02-08T00:00:00+03:00"),
                    new EventDto("adina", "До дня рождения Адины", "2027-08-30T00:00:00+03:00")
            );
        }

        @GetMapping("/notify-visit")
        public String notifyVisit(HttpServletRequest request) {
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");
            notifier.sendNotification("🚨 Заход на сайт\nIP: " + ip + "\nБраузер: " + ua);
            return "ok";
        }

        @PostMapping("/feedback")
        public String feedback(@RequestBody Map<String, String> body, HttpServletRequest request) {
            String sessionId = body.get("sessionId");
            String message = body.get("message");
            if (sessionId == null || message == null || message.trim().isEmpty()) return "invalid";

            FeedbackService.Session s = feedbackService.getOrCreate(sessionId);
            if (s.closed) return "closed";

            feedbackService.addUserMessage(sessionId, message);
            String ip = getClientIp(request);

            // Отправляем уведомление в ТГ (без кнопок, просто текст)
            notifier.sendNotification("💬 Новое сообщение!\nID: " + sessionId + "\nIP: " + ip + "\n\n" + message + "\n\n👉 Ответить: https://marat-marat112w.msk1.amvera.tech/admin.html");

            return "ok";
        }

        @GetMapping("/feedback/{sessionId}")
        public Map<String, Object> getFeedback(@PathVariable String sessionId) {
            FeedbackService.Session s = feedbackService.get(sessionId);
            if (s == null) return Map.of("messages", List.of(), "closed", false);
            List<Map<String, Object>> msgs = new ArrayList<>();
            for (FeedbackService.ChatMessage m : s.messages) {
                msgs.add(Map.of("from", m.from, "text", m.text, "time", m.time));
            }
            return Map.of("messages", msgs, "closed", s.closed);
        }

        // ===== АДМИН ПАНЕЛЬ =====
        @PostMapping("/admin/login")
        public String login(@RequestBody Map<String, String> body, HttpSession session) {
            if ("admin123".equals(body.get("password"))) {
                session.setAttribute("ADMIN", true);
                session.setMaxInactiveInterval(30 * 24 * 60 * 60); // Не выкидывать 30 дней
                return "ok";
            }
            return "wrong";
        }

        @GetMapping("/admin/check")
        public String checkAdmin(HttpSession session) {
            return Boolean.TRUE.equals(session.getAttribute("ADMIN")) ? "ok" : "no";
        }

        @GetMapping("/admin/sessions")
        public List<Map<String, Object>> getAllSessions(HttpSession session) {
            if (!Boolean.TRUE.equals(session.getAttribute("ADMIN"))) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, FeedbackService.Session> entry : feedbackService.getAllSessions().entrySet()) {
                FeedbackService.Session s = entry.getValue();
                List<Map<String, Object>> msgs = new ArrayList<>();
                for (FeedbackService.ChatMessage m : s.messages) {
                    msgs.add(Map.of("from", m.from, "text", m.text, "time", m.time));
                }
                result.add(Map.of(
                        "sessionId", entry.getKey(),
                        "closed", s.closed,
                        "messages", msgs
                ));
            }
            return result;
        }

        @PostMapping("/admin/reply")
        public String reply(@RequestBody Map<String, String> body, HttpSession session) {
            if (!Boolean.TRUE.equals(session.getAttribute("ADMIN"))) return "unauthorized";
            String sessionId = body.get("sessionId");
            String text = body.get("text");
            if (sessionId != null && text != null) {
                feedbackService.addAdminMessage(sessionId, text);
                return "ok";
            }
            return "error";
        }

        @PostMapping("/admin/close")
        public String closeChat(@RequestBody Map<String, String> body, HttpSession session) {
            if (!Boolean.TRUE.equals(session.getAttribute("ADMIN"))) return "unauthorized";
            String sessionId = body.get("sessionId");
            if (sessionId != null) {
                feedbackService.close(sessionId);
                return "ok";
            }
            return "error";
        }

        private String getClientIp(HttpServletRequest request) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            return request.getRemoteAddr();
        }
    }
}