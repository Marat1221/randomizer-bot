package com.example.demo.entity;

import com.example.demo.entity.config.BotConfig;
import lombok.AllArgsConstructor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.demo")
public class Main {

    public static void main(String... args) {
        SpringApplication.run(Main.class, args);
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

            // Получаем или создаём игровую сессию для текущего чата
            GameState state = gameSessions.computeIfAbsent(chatId, k -> new GameState());

            // 1. КОМАНДА ЗАПУСКА ИГРЫ
            if (text.equals("/start_game_random")) {
                state.isGameActive = true;
                state.isWaitingForName = false;
                state.players.clear();
                sendText(chatId, "🎮 Игра \"Рандомайзер\" запущена!\n\n" +
                        "Используйте команду /add, чтобы добавить участников.\n" +
                        "Затем введите /start_random [название], чтобы выбрать случайного.\n" +
                        "Для выхода используйте /exit.");
                return;
            }

            // 2. КОМАНДА ВЫХОДА ИЗ ИГРЫ
            if (text.equals("/exit")) {
                if (!state.isGameActive) {
                    sendText(chatId, "Игра ещё даже не запущена. Наберите /start_game_random");
                    return;
                }
                state.isGameActive = false;
                state.isWaitingForName = false;
                state.players.clear();
                gameSessions.remove(chatId); // Освобождаем сессию, чтобы не копить память
                sendText(chatId, "🚪 Грустно, что вы вышли :(. Список участников полностью очищен.");
                return;
            }

            // 3. ЕСЛИ ЖДЁМ ИМЯ И ЭТО НЕ КОМАНДА — ДОБАВЛЯЕМ ИГРОКА
            if (state.isWaitingForName && !text.startsWith("/")) {
                state.players.add(text);
                state.isWaitingForName = false;
                sendText(chatId, "✅ Добавлен: " + text + "\nВсего участников в списке: " + state.players.size());
                return;
            }

            // Если ждали имя, но пришла команда — отменяем ожидание и обрабатываем команду как обычно
            if (state.isWaitingForName) {
                state.isWaitingForName = false;
            }

            // 4. ОБРАБОТКА ИГРОВЫХ КОМАНД
            if (state.isGameActive) {

                // А) Команда добавления игрока
                if (text.equals("/add")) {
                    state.isWaitingForName = true;
                    sendText(chatId, "📝 Введите имя игрока (сущности):");
                    return;
                }

                // Б) Команда розыгрыша
                if (text.equals("/start_random") || text.startsWith("/start_random ")) {
                    if (state.players.isEmpty()) {
                        sendText(chatId, "❌ Список участников пуст! Сначала добавьте кого-нибудь через /add");
                        return;
                    }

                    // Вытаскиваем назначение (всё, что идёт после "/start_random ")
                    String title = "номинацию";
                    if (text.contains(" ")) {
                        title = text.substring(text.indexOf(" ") + 1).trim();
                    }
                    if (title.isEmpty()) {
                        title = "номинацию";
                    }

                    // Снимок списка игроков — чтобы за время паузы список не изменился под нами
                    final List<String> snapshot = new ArrayList<>(state.players);
                    final String finalTitle = title;

                    sendText(chatId, "🎰 Барабан крутится, выбираем, кто тут [" + finalTitle + "]...");

                    // Пауза и результат — в отдельном потоке, чтобы не блокировать polling всех чатов
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        String winner = snapshot.get(random.nextInt(snapshot.size()));
                        sendText(chatId, "🎉 Результат определён!\n\n👉 " + winner + " -> " + finalTitle + "!");
                    });
                    return;
                }
            } else {
                // Если игра не активна, но пользователь пишет игровые команды — подсказываем
                if (text.equals("/add") || text.equals("/start_random") || text.startsWith("/start_random ")) {
                    sendText(chatId, "⚠️ Чтобы начать добавлять людей и крутить рандомайзер, сначала активируйте игру командой: /start_game_random");
                    return;
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