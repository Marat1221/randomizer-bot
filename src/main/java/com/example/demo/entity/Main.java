package com.example.demo.entity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@ComponentScan(basePackages = "com.example.demo")
public class Main {

    public static void main(String... args) {
        SpringApplication.run(Main.class, args);
    }

    @RestController
    @RequestMapping("/api")
    public static class CounterController {

        @Value("${telegram.bot.token:}")
        private String botToken;

        @Value("${telegram.chat.id:}")
        private String chatId;

        private TelegramClient telegramClient;

        // Кулдаун для обратной связи (IP -> время последнего сообщения)
        private final Map<String, Long> feedbackCooldown = new ConcurrentHashMap<>();

        private TelegramClient getTelegramClient() {
            if (telegramClient == null && botToken != null && !botToken.isBlank()) {
                telegramClient = new OkHttpTelegramClient(botToken);
            }
            return telegramClient;
        }

        @GetMapping("/value")
        public long getServerTime() {
            return System.currentTimeMillis();
        }

        @GetMapping("/events")
        public List<EventDto> getEvents() {
            return List.of(
                    new EventDto("newyear", "До Нового Года", "2027-01-01T00:00:00+03:00"),
                    new EventDto("ramadan", "До Рамадана", "2027-02-08T00:00:00+03:00"),
                    new EventDto("adina", "До дня рождения Адины", "2027-08-30T00:00:00+03:00")
            );
        }

        @Autowired private FeedbackService feedbackService;
        @Autowired private FeedbackBot feedbackBot;

        @GetMapping("/notify-visit")
        public String notifyVisit(HttpServletRequest request) {
            TelegramClient client = feedbackBot.getClient();
            String chatId = feedbackBot.getAdminChatId();
            if (client == null || chatId == null || chatId.isBlank()) return "disabled";
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");
            try {
                client.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("🚨 Новый заход на сайт!\nIP: " + ip + "\nБраузер: " + ua)
                        .build());
                return "ok";
            } catch (Exception e) { e.printStackTrace(); return "error"; }
        }

        // ===== НОВЫЙ ЭНДПОИНТ: ОБРАТНАЯ СВЯЗЬ =====
        @PostMapping("/feedback")
        public String feedback(@RequestBody Map<String, String> body, HttpServletRequest request) {
            String sessionId = body.get("sessionId");
            String message = body.get("message");

            // Валидация
            if (sessionId == null || sessionId.isBlank() || message == null || message.trim().isEmpty()) {
                return "invalid";
            }
            if (message.length() > 500) {
                return "toolong";
            }

            // Защита от спама (5 сек между сообщениями с одного IP)
            String ip = getClientIp(request);
            Long last = feedbackCooldown.get(ip);
            long now = System.currentTimeMillis();
            if (last != null && now - last < 5000) {
                return "cooldown";
            }

            TelegramClient client = getTelegramClient();
            if (client == null || chatId == null || chatId.isBlank()) return "disabled";

            try {
                feedbackCooldown.put(ip, now);
                String text = "💬 Обратная связь #" + sessionId + "\n" +
                        "IP: " + ip + "\n\n" +
                        message;
                SendMessage sm = SendMessage.builder().chatId(chatId).text(text).build();
                client.execute(sm);
                return "ok";
            } catch (Exception e) {
                e.printStackTrace();
                return "error";
            }
        }

        private String getClientIp(HttpServletRequest request) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            return request.getRemoteAddr();
        }
    }
}