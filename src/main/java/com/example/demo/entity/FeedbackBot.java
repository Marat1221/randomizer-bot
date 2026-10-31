package com.example.demo.entity;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeedbackBot implements SpringLongPollingBot {

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String adminChatId;

    private final FeedbackService feedbackService;
    private TelegramClient client;

    // chatId админа -> sessionId, для которого ждём ответ
    private final Map<String, String> awaitingReply = new ConcurrentHashMap<>();

    public FeedbackBot(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostConstruct
    public void init() {
        if (botToken != null && !botToken.isBlank()) {
            client = new OkHttpTelegramClient(botToken);
        }
    }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updates -> {
            for (Update u : updates) {
                try { handleUpdate(u); } catch (Exception e) { e.printStackTrace(); }
            }
        };
    }

    private void handleUpdate(Update update) throws Exception {
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleAdminMessage(update);
        }
    }

    private void handleCallback(CallbackQuery cb) throws Exception {
        String data = cb.getData();
        String chatId = cb.getMessage().getChatId().toString();
        String sessionId = data.length() > 6 ? data.substring(6) : "";
        if (client == null) return;

        if (data.startsWith("reply:")) {
            awaitingReply.put(chatId, sessionId);
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("✏️ Напиши ответ пользователю #" + sessionId + " следующим сообщением.")
                    .build());
        } else if (data.startsWith("close:")) {
            feedbackService.close(sessionId);
            awaitingReply.remove(chatId);
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("✅ Диалог #" + sessionId + " закрыт.")
                    .build());
        }
        client.execute(AnswerCallbackQuery.builder().callbackQueryId(cb.getId()).build());
    }

    private void handleAdminMessage(Update update) throws Exception {
        String chatId = update.getMessage().getChatId().toString();
        if (!chatId.equals(adminChatId)) return; // только ты

        String text = update.getMessage().getText().trim();
        String sessionId = awaitingReply.get(chatId);

        if (sessionId != null && !text.startsWith("/")) {
            feedbackService.addAdminMessage(sessionId, text);
            awaitingReply.remove(chatId);
            if (client != null) {
                client.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("📤 Отправлено пользователю #" + sessionId)
                        .build());
            }
        }
    }

    // Вызывается из контроллера — отправляет сообщение админу с кнопками
    public boolean notifyAdmin(String sessionId, String ip, String message) {
        if (client == null || adminChatId == null || adminChatId.isBlank()) return false;
        try {
            // ВАЖНО: используем InlineKeyboardRow вместо List<List<...>>
            InlineKeyboardRow row = new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text("✏️ Ответить")
                            .callbackData("reply:" + sessionId)
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("✅ Закрыть")
                            .callbackData("close:" + sessionId)
                            .build()
            );
            InlineKeyboardMarkup kb = new InlineKeyboardMarkup(List.of(row));

            client.execute(SendMessage.builder()
                    .chatId(adminChatId)
                    .text("💬 Новое сообщение #" + sessionId + "\nIP: " + ip + "\n\n" + message)
                    .replyMarkup(kb)
                    .build());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}