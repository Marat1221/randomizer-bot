package com.example.demo.entity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.LongPollingBot; // ИСПРАВЛЕНО: Импортируем LongPollingBot
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotInitializer {

    // ИСПРАВЛЕНО: Меняем тип с TelegramBot на LongPollingBot
    private final LongPollingBot telegramBot;

    @Autowired
    public BotInitializer(LongPollingBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(telegramBot);
            System.out.println("--- БОТ УСПЕШНО ЗАРЕГИСТРИРОВАН В TELEGRAM! ---");
        } catch (TelegramApiException e) {
            System.out.println("Ошибка при регистрации бота в Telegram API!");
            e.printStackTrace();
        }
    }
}
