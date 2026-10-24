package com.example.demo.entity;

import com.example.demo.entity.config.BotConfig;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.demo")
public class Main implements CommandLineRunner {

    public static void main(String... args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) {
        // CommandLineRunner оставляем пустым
    }

    // Вспомогательный класс для хранения состояния игры в конкретном чате
    public static class GameState {
        private boolean isGameActive = false;
        private boolean isWaitingForName = false;
        private final List<String> players = new ArrayList<>();
    }

    @Component
    @AllArgsConstructor
    public static class TelegramBot extends TelegramLongPollingBot {

        private final BotConfig botConfig;

        // Хранилище игровых сессий (ключ — chatId, значение — состояние игры)
        // Позволяет боту работать в разных чатах одновременно и не путать списки
        private final Map<Long, GameState> gameSessions = new ConcurrentHashMap<>();
        private final Random random = new Random();

        @Override
        public String getBotUsername() {
            return botConfig.getBotName();
        }

        @Override
        public String getBotToken() {
            return botConfig.getToken();
        }

        @Override
        public void onUpdateReceived(Update update) {
            // Обрабатываем только текстовые сообщения
            if (!update.hasMessage() || !update.getMessage().hasText()) {
                return;
            }

            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();

            // Получаем или создаем игровую сессию для текущего чата
            GameState state = gameSessions.computeIfAbsent(chatId, k -> new GameState());

            // 1. КОМАНДА ЗАПУСКА ИГРЫ
            if (text.equals("/start_game_random")) {
                state.isGameActive = true;
                state.isWaitingForName = false;
                state.players.clear(); // Очищаем список старых игроков
                sendText(chatId, "🎮 Игра \"Рандомайзер\" запущена!\n\n" +
                        "Используйте команду /add, чтобы добавить участников.\n" +
                        "Затем введите ?randoms [название], чтобы выбрать случайного.\n" +
                        "Для выхода используйте /exit." +
                        "чтобы начать -> /start_random. :)");
                return;
            }

            // 2. КОМАНДА ВЫХОДА ИЗ ИГРЫ
            if (text.equals("/exit")) {
                if (!state.isGameActive) {
                    sendText(chatId, "Игра еще даже не запущена. Наберите /start_game_random");
                    return;
                }
                state.isGameActive = false;
                state.isWaitingForName = false;
                state.players.clear();
                sendText(chatId, "🚪 Грустно, что вы вышли :(. Список участников полностью очищен.");
                return;
            }

            // Если игра активна, обрабатываем игровые команды
            if (state.isGameActive) {

                // А) Команда добавления сущности
                if (text.equals("/add")) {
                    state.isWaitingForName = true;
                    sendText(chatId, "📝 Введите имя игроманов (сущность):");
                    return;
                }

                // Б) Перехват ввода имени после команды /add
                if (state.isWaitingForName) {
                    state.players.add(text);
                    state.isWaitingForName = false; // Сбрасываем флаг ожидания текста
                    sendText(chatId, "✅ Добавлен: " + text + "\nВсего участников в списке: " + state.players.size());
                    return;
                }

                // В) Команда розыгрыша назначения через префикс ?randoms
                if (text.startsWith("/start_random")) {
                    if (state.players.isEmpty()) {
                        sendText(chatId, "❌ Список участников пуст! Сначала добавьте кого-нибудь через /add");
                        return;
                    }

                    // Вытаскиваем само назначение (все, что идет после "?randoms ")
                    String title = "номинацию";
                    if (text.contains(" ")) {
                        title = text.substring(text.indexOf(" ") + 1).trim();
                    }

                    if (title.isEmpty()) {
                        title = "номинацию";
                    }

                    // Имитация кручения барабана
                    sendText(chatId, "🎰 Барабан крутится, выбираем, кто тут [" + title + "]...");

                    try {
                        Thread.sleep(1500); // Небольшая пауза для интриги
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Выбираем случайного человека
                    int randomIndex = random.nextInt(state.players.size());
                    String winner = state.players.get(randomIndex);

                    sendText(chatId, "🎉 Результат определен!\n\n👉 " + winner + " -> " + title + "!");
                    return;
                }
            } else {
                // Если пользователь пишет команды /add или ?randoms, но игра не запущена
                if (text.equals("/add") || text.startsWith("?randoms")) {
                    sendText(chatId, "⚠️ Чтобы начать добавлять людей и крутить рандомайзер, сначала активируйте игру командой: /start_game_random");
                }
            }
        }

        // Удобный метод для быстрой отправки текстовых сообщений
        private void sendText(Long chatId, String text) {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
