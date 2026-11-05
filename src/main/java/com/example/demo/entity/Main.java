package com.example.demo.entity;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // <-- ДОБАВИЛИ ИМПОРТ
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

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
        @Autowired private OpenRouterExample openRouterExample;
        @Autowired private SharedEventRepository sharedEventRepo;

        // ВОТ ТУТ ПОДТЯГИВАЕТСЯ ПАРОЛЬ ИЗ application.properties
        @Value("${admin.code}")
        private String adminCode;

        // ===== ПУБЛИЧНЫЕ ЭНДПОИНТЫ =====
        @GetMapping("/value")
        public long getServerTime() { return System.currentTimeMillis(); }

        @GetMapping("/events")
        public List<EventDto> getEvents() {
            return List.of(
                    new EventDto("newyear", "До Нового Года", "2027-01-01T00:00:00"),
                    new EventDto("ramadan", "До Рамадана", "2027-02-08T00:00:00"),
                    new EventDto("adina", "До дня рождения Адины", "2027-08-30T00:00:00"),
                    new EventDto("myrzaiym", "До дня рождения Мырзайыма", "2027-01-21T00:00:00"),
                    new EventDto("leto", "До лета", "2027-06-01T00:00:00")
            );
        }
        // Импорты сверху файла (добавь если нет):
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

        @Autowired private PersonalEventRepository personalRepo;
        @Autowired private ObjectMapper objectMapper;

// ===== ЛИЧНЫЕ СОБЫТИЯ (без регистрации, по токену) =====

        @GetMapping("/personal-events")
        public Map<String, Object> getPersonalEvents(@RequestParam String token) {
            // Создаём явный Map<String, Object> — Java не будет гадать
            Map<String, Object> result = new HashMap<>();
            result.put("events", new ArrayList<>()); // по умолчанию пустой массив

            if (token == null || token.isBlank() || token.length() > 64) {
                return result;
            }

            Optional<PersonalEvent> opt = personalRepo.findById(token);
            if (opt.isPresent()) {
                try {
                    List<?> events = objectMapper.readValue(opt.get().getEventsJson(), List.class);
                    result.put("events", events);
                } catch (Exception e) {
                    // Если JSON битый — молча оставляем пустой список
                }
            }

            return result;
        }
        @GetMapping("/ai")
        public String getTextAi(@RequestParam(defaultValue = "Привет! Как дела?") String message) {
            try {
                return openRouterExample.ask(message);
            } catch (Exception e) {
                e.printStackTrace();
                return "Ошибка: " + e.getMessage();
            }
        }
        @PostMapping("/telegram/webhook")
        public String telegramWebhook(@RequestBody Update update) {
            notifier.handleUpdate(update);
            return "ok";
        }
        @PostMapping("/share")
        public ResponseEntity<?> createShare(@RequestBody Map<String, String> body) {
            String title = body.get("title");
            String emoji = body.get("emoji");
            String date = body.get("date");
            String color = body.get("color");

            // 1. ВАЛИДАЦИЯ (как ты просил)
            if (title == null || title.trim().isEmpty() || title.length() > 100) {
                return ResponseEntity.badRequest().body(Map.of("error", "Title is invalid"));
            }
            if (date == null || date.trim().isEmpty() || date.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of("error", "Date is invalid"));
            }
            // Если emoji или color не передали, ставим дефолт (чтобы база не ругалась)
            if (emoji == null) emoji = "⏱️";
            if (color == null) color = "#3b82f6";

            // 2. ГЕНЕРАЦИЯ УНИКАЛЬНОГО ID
            String shareId;
            do {
                shareId = generateShortId();
            } while (sharedEventRepo.existsById(shareId)); // Проверка на уникальность

            // 3. СОХРАНЕНИЕ
            SharedEvent event = new SharedEvent(shareId, title, emoji, date, color);
            sharedEventRepo.save(event);

            // 4. ОТВЕТ
            return ResponseEntity.ok(Map.of("shareId", shareId));
        }


        @GetMapping("/share/{shareId}")
        public ResponseEntity<?> getShare(@PathVariable String shareId) {
            return sharedEventRepo.findById(shareId)
                    .map(event -> ResponseEntity.ok(Map.of(
                            "title", event.getTitle(),
                            "emoji", event.getEmoji(),
                            "date", event.getDate(),
                            "color", event.getColor()
                    )))
                    .orElse(ResponseEntity.notFound().build()); // 404, как договаривались
        }

        // Вспомогательный метод для генерации ID (6 символов: буквы + цифры)
        private String generateShortId() {
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder(6);
            java.security.SecureRandom random = new java.security.SecureRandom();
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        }

        @PostMapping("/personal-events")
        public ResponseEntity<?> savePersonalEvents(
                @RequestHeader(value = "X-Personal-Token", required = false) String token,
                @RequestBody Map<String, Object> body) {

            if (token == null || token.isBlank() || token.length() > 64) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid token"));
            }

            Object eventsObj = body.get("events");
            if (!(eventsObj instanceof List)) {
                return ResponseEntity.badRequest().body(Map.of("error", "events must be array"));
            }
            List<?> events = (List<?>) eventsObj;

            // Ограничение на размер, чтобы не залили мусор
            if (events.size() > 100) {
                return ResponseEntity.badRequest().body(Map.of("error", "Too many events (max 100)"));
            }

            try {
                String json = objectMapper.writeValueAsString(events);
                // Ограничение на размер JSON, чтобы не забили базу
                if (json.length() > 100_000) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Payload too large"));
                }

                PersonalEvent pe = personalRepo.findById(token).orElse(new PersonalEvent(token, "[]"));
                pe.setEventsJson(json);
                personalRepo.save(pe);

                return ResponseEntity.ok(Map.of("ok", true));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", "Save failed"));
            }
        }



        @GetMapping("/notify-visit")
        public String notifyVisit(HttpServletRequest request) {
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");
            notifier.sendNotification("🚨 Заход на сайт\nIP: " + ip + "\nБраузер: " + ua);
            return "ok";
        }
        @Autowired private VisitStatsRepository visitRepo;

        @GetMapping("/visits/main")
        public Long incrementVisit() {
            // 1. Ищем в БД запись с ID "main". Если нет — создаем новую с нулем.
            VisitStats stats = visitRepo.findById("main")
                    .orElse(new VisitStats("main", 0L));

            // 2. Просто меняем поле в объекте Java
            stats.setVisitCount(stats.getVisitCount() + 1);

            // 3. Spring Boot сам сделает UPDATE (или INSERT), когда мы вызовем save()
            visitRepo.save(stats);

            // 4. Возвращаем число
            return stats.getVisitCount();
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
            // СВЕРЯЕМ С ПЕРЕМЕННОЙ ИЗ НАСТРОЕК
            String incomingPass = body.get("password");
            if (incomingPass != null && incomingPass.equals(adminCode)) {
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