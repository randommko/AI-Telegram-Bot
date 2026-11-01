package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

@Component
public class BotLifecycle implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(BotLifecycle.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TelegramBotsApi botsApi;
    private final TelegramLongPollingBot bot;
    private BotSession session;

    public BotLifecycle(TelegramBotsApi botsApi, TelegramLongPollingBot bot) {
        this.botsApi = botsApi;
        this.bot = bot;
    }

    @Override
    public void start() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                session = botsApi.registerBot(bot);
                logger.info("Бот успешно запущен на попытке №{}", attempt);
                return;
            } catch (TelegramApiException e) {
                logger.error("Ошибка при запуске бота на попытке №{}", attempt, e);
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("Бот не запустился после " + MAX_ATTEMPTS + " попыток.", e);
                }
                logger.info("Повторная попытка запуска...");
            }
        }
    }

    @Override
    public void stop() {
        if (session != null) {
            session.stop();
            session = null;
            logger.info("Опрос обновлений Telegram остановлен");
        }
    }

    @Override
    public boolean isRunning() {
        return session != null && session.isRunning();
    }
}
