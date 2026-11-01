package org.example;

import org.example.CockSize.CockSizeGame;
import org.example.Horoscope.HoroscopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

@Component
public class CallbackDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CallbackDispatcher.class);
    private final MessageSender messageSender;
    private final HoroscopeService horoscopeService;
    private final CockSizeGame cockSizeGame;

    // Map для диспетчеризации callback (O(1) поиск)
    private final Map<String, CallbackHandler> handlers = Map.of(
            "avg_cock_size_button_pressed", this::handleAvgCockSize
    );

    // Функциональный интерфейс для хендлеров
    @FunctionalInterface
    public interface CallbackHandler {
        void handle(CallbackQuery callback, MessageSender sender);
    }

    public CallbackDispatcher(MessageSender messageSender,
                              HoroscopeService horoscopeService,
                              CockSizeGame cockSizeGame) {
        this.messageSender = messageSender;
        this.horoscopeService = horoscopeService;
        this.cockSizeGame = cockSizeGame;
    }

    public void dispatch(CallbackQuery callback) {
        String callbackData = callback.getData();
        logger.debug("Callback: {}", callbackData);

        // 1. Сначала проверяем ЗОДИАКИ через enum (12 кнопок → 3 строки!)
        KeyboardBuilder.Zodiac zodiac = KeyboardBuilder.Zodiac.fromCallback(callbackData);
        if (zodiac != null) {
            handleZodiac(callback, zodiac);
            answerCallbackQuery(callback);
            return;
        }

        // 2. Потом остальные callbacks
        CallbackHandler handler = handlers.get(callbackData);
        if (handler != null) {
            handler.handle(callback, messageSender);
            answerCallbackQuery(callback);
        } else {
            logger.warn("Неизвестный callback: {}", callbackData);
            answerCallbackQuery(callback, "Неизвестная кнопка");
        }
    }


    // Универсальный answer callback (убирает "часики")
    private void answerCallbackQuery(CallbackQuery callback) {
        answerCallbackQuery(callback, null);
    }

    private void answerCallbackQuery(CallbackQuery callback, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callback.getId())
                .text(text)
                .build();
        try {
            messageSender.getBot().execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Ошибка answerCallback: ", e);
        }
    }

    // Хендлеры
    private void handleZodiac(CallbackQuery callback, KeyboardBuilder.Zodiac zodiac) {
        horoscopeService.sendHoroscope(callback, zodiac.getId(), "today");
    }

    private void handleAvgCockSize(CallbackQuery callback, MessageSender sender) {
        cockSizeGame.sendAVGCockSize(callback);
    }

}
