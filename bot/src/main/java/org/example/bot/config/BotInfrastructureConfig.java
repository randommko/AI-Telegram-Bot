package org.example.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bot.Delay;
import org.example.core.config.BotProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.ExponentialBackOff;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.Executors;

/** Всё, что нужно только боту: клиент Telegram, опрос обновлений и пауза между репликами. */
@Configuration
public class BotInfrastructureConfig {

    /**
     * Потолок паузы между повторами GetUpdates. По умолчанию ExponentialBackOff после
     * 15 минут недоступности Telegram возвращает 900000 мс, и бот молчит ещё четверть часа
     * даже когда сеть уже восстановилась.
     */
    private static final int MAX_RETRY_PAUSE_MILLIS = 180_000;

    /** Пауза между репликами; прерывание не должно ронять ответ. */
    @Bean
    Delay delay() {
        return millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Клиент Telegram. Токен проверяется здесь, иначе пустое значение всплывёт
     * невнятным отказом HTTP вместо ошибки конфигурации.
     */
    @Bean
    TelegramClient telegramClient(BotProperties properties) {
        String token = properties.token();
        if (token == null || token.isBlank())
            throw new IllegalStateException("BOT_TOKEN не задан: бот не может подключиться к Telegram");
        return new OkHttpTelegramClient(token);
    }

    /**
     * Приложение long polling: регистрацию и остановку ведёт BotLifecycle.
     * Три первые зависимости повторяют умолчания библиотеки — иначе до BackOff не добраться.
     */
    @Bean
    TelegramBotsLongPollingApplication telegramBotsApplication() {
        return new TelegramBotsLongPollingApplication(
                ObjectMapper::new,
                new TelegramOkHttpClientFactory.DefaultOkHttpClientCreator(),
                Executors::newSingleThreadScheduledExecutor,
                () -> new ExponentialBackOff.Builder()
                        .setMaxElapsedTimeMillis(MAX_RETRY_PAUSE_MILLIS)
                        .build());
    }
}
