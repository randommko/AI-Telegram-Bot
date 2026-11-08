package org.example.bot;

import org.example.core.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class BotLifecycle implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(BotLifecycle.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(5);

    private final TelegramBotsLongPollingApplication application;
    private final LongPollingSingleThreadUpdateConsumer bot;
    private final BotProperties properties;
    private final Duration stopTimeout;
    private BotSession session;

    @Autowired
    public BotLifecycle(TelegramBotsLongPollingApplication application,
                        LongPollingSingleThreadUpdateConsumer bot,
                        BotProperties properties) {
        this(application, bot, properties, DEFAULT_STOP_TIMEOUT);
    }

    BotLifecycle(TelegramBotsLongPollingApplication application, LongPollingSingleThreadUpdateConsumer bot,
                 BotProperties properties, Duration stopTimeout) {
        this.application = application;
        this.bot = bot;
        this.properties = properties;
        this.stopTimeout = stopTimeout;
    }

    @Override
    public void start() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                session = application.registerBot(properties.token(), bot);
                logger.info("Бот успешно запущен на попытке №{}", attempt);
                return;
            } catch (TelegramApiException e) {
                closeFailedSession();
                logger.error("Ошибка при запуске бота на попытке №{}", attempt, e);
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("Бот не запустился после " + MAX_ATTEMPTS + " попыток.", e);
                }
                logger.info("Повторная попытка запуска...");
            }
        }
    }

    /** Неудачная регистрация могла оставить сессию внутри приложения: снимаем, чтобы не было двух опросов. */
    private void closeFailedSession() {
        try {
            application.unregisterBot(properties.token());
        } catch (TelegramApiException e) {
            logger.warn("Не удалось снять неудавшуюся сессию Telegram: {}", e.toString());
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    /**
     * Остановка не должна держать shutdown-hook дольше systemd TimeoutStopSec:
     * BotSession.stop() закрывает HTTP-клиент и может зависнуть на активном long polling.
     */
    @Override
    public void stop() {
        BotSession current = session;
        session = null;
        if (current == null) {
            return;
        }

        logger.info("Останавливаю опрос обновлений Telegram");
        CompletableFuture<Void> stopCall = CompletableFuture.runAsync(() -> {
            try {
                if (current.isRunning()) {
                    current.stop();
                }
            } catch (RuntimeException e) {
                logger.warn("Остановка сессии Telegram завершилась ошибкой: {}", e.toString());
            }
        });

        try {
            stopCall.get(stopTimeout.toMillis(), TimeUnit.MILLISECONDS);
            logger.info("Опрос обновлений Telegram остановлен");
        } catch (TimeoutException e) {
            logger.warn("Сессия Telegram не остановилась за {} с — завершаем процесс без ожидания",
                    stopTimeout.toSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Ожидание остановки сессии Telegram прервано");
        } catch (Exception e) {
            logger.warn("Ошибка при ожидании остановки сессии Telegram: {}", e.toString());
        }
    }

    @Override
    public boolean isRunning() {
        return session != null && session.isRunning();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
