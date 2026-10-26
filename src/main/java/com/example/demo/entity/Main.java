package com.example.demo.entity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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

        // Токен и chat_id берутся из application.properties (или переменных окружения Amvera)
        @Value("${telegram.bot.token:}")
        private String botToken;

        @Value("${telegram.chat.id:}")
        private String chatId;

        private TelegramClient telegramClient;

        private TelegramClient getTelegramClient() {
            if (telegramClient == null && botToken != null && !botToken.isBlank()) {
                telegramClient = new OkHttpTelegramClient(botToken);
            }
            return telegramClient;
        }

        // Точное время сервера для синхронизации счётчика
        @GetMapping("/value")
        public long getServerTime() {
            return System.currentTimeMillis();
        }

        // Уведомление о заходе на сайт
        @GetMapping("/notify-visit")
        public String notifyVisit(HttpServletRequest request) {
            TelegramClient client = getTelegramClient();
            if (client == null || chatId == null || chatId.isBlank()) {
                return "disabled";
            }

            String ip = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String referer = request.getHeader("Referer");

            StringBuilder text = new StringBuilder();
            text.append("🚨 Новый заход на сайт!\n");
            text.append("IP: ").append(ip).append("\n");
            text.append("Браузер: ").append(userAgent != null ? userAgent : "—");
            if (referer != null && !referer.isBlank()) {
                text.append("\nОткуда: ").append(referer);
            }

            try {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(text.toString())
                        .build();
                client.execute(message);
                return "ok";
            } catch (Exception e) {
                e.printStackTrace();
                return "error";
            }
        }

        private String getClientIp(HttpServletRequest request) {
            // Amvera (и другие прокси) передают реальный IP в X-Forwarded-For
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
    }
}