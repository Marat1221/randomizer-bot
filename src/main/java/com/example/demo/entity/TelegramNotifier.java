package com.example.demo.entity;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class TelegramNotifier {

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String adminChatId;

    @Autowired
    private OpenRouterExample openRouterExample;

    private OkHttpTelegramClient client;

    @PostConstruct
    public void init() {
        if (botToken != null && !botToken.isBlank()) {
            client = new OkHttpTelegramClient(botToken);
        }
    }

    public boolean sendNotification(String text) {
        return sendTo(adminChatId, text);
    }

    /** Отправка в конкретный чат (нужно для ответов пользователям). */
    public boolean sendTo(String chatId, String text) {
        if (client == null || chatId == null || chatId.isBlank()) return false;
        try {
            String safe = text == null ? "" : text;
            if (safe.length() > 4000) safe = safe.substring(0, 4000) + "…";
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(safe)
                    .build());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Обрабатывает входящий Update от Telegram. */
    public void handleUpdate(Update update) {
        if (update == null || !update.hasMessage()) return;
        Message msg = update.getMessage();
        if (!msg.hasText()) return;

        String text = msg.getText().trim();
        String lower = text.toLowerCase();

        // Реагируем только если сообщение начинается со слова "бот"
        if (!lower.startsWith("бот")) return;

        // И после "бот" идёт пробел или конец сообщения (а не "ботаника")
        if (text.length() > 3 && !Character.isWhitespace(text.charAt(3))) return;

        String chatId = String.valueOf(msg.getChatId());
        String query = text.substring(3).trim();

        if (query.isEmpty()) {
            sendTo(chatId, "Привет! Напиши: «бот <вопрос>» — отвечу через ИИ.");
            return;
        }

        String answer;
        try {
            answer = openRouterExample.ask(query);
            if (answer == null || answer.isBlank()) answer = "ИИ вернул пустой ответ.";
        } catch (Exception e) {
            answer = "Ошибка ИИ: " + e.getMessage();
        }
        sendTo(chatId, answer);
    }
}