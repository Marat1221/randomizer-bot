package com.example.demo.entity;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramNotifier {

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String adminChatId;

    private TelegramClient client;

    @PostConstruct
    public void init() {
        if (botToken != null && !botToken.isBlank()) {
            client = new OkHttpTelegramClient(botToken);
        }
    }

    public boolean sendNotification(String text) {
        if (client == null || adminChatId == null || adminChatId.isBlank()) return false;
        try {
            client.execute(SendMessage.builder()
                    .chatId(adminChatId)
                    .text(text)
                    .build());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}