package org.example;

import org.example.AiChat.AiService;
import org.example.CockSize.CockSizeGame;
import org.example.Horoscope.HoroscopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;
import java.util.Optional;

@Component
public class CallbackDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CallbackDispatcher.class);
    private final MessageSender messageSender;
    private final HoroscopeService horoscopeService;
    private final CockSizeGame cockSizeGame;
    private final AiService aiService;

    // Map для диспетчеризации callback (O(1) поиск)
    private final Map<String, CallbackHandler> handlers = Map.of(
            "avg_cock_size_button_pressed", this::handleAvgCockSize
    );

    // Функциональный интерфейс для хендлеров: возвращенный текст показывается всплывающим уведомлением
    @FunctionalInterface
    public interface CallbackHandler {
        String handle(CallbackQuery callback, MessageSender sender);
    }

    public CallbackDispatcher(MessageSender messageSender,
                              HoroscopeService horoscopeService,
                              CockSizeGame cockSizeGame,
                              AiService aiService) {
        this.messageSender = messageSender;
        this.horoscopeService = horoscopeService;
        this.cockSizeGame = cockSizeGame;
        this.aiService = aiService;
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

        // 2. Подтверждение сброса памяти AI: данные кнопки уникальны для каждого чата и автора
        Optional<AiClearCallback> aiClear = AiClearCallback.parse(callbackData);
        if (aiClear.isPresent()) {
            answerCallbackQuery(callback, handleAiClear(callback, aiClear.get()));
            return;
        }

        // 3. Потом остальные callbacks
        CallbackHandler handler = handlers.get(callbackData);
        if (handler != null) {
            answerCallbackQuery(callback, handler.handle(callback, messageSender));
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

    private String handleAvgCockSize(CallbackQuery callback, MessageSender sender) {
        cockSizeGame.sendAVGCockSize(callback);
        return null;
    }

    private String handleAiClear(CallbackQuery callback, AiClearCallback data) {
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        if (!data.authorId().equals(callback.getFrom().getId())) {
            logger.warn("Кнопку сброса памяти AI в чате {} нажал другой пользователь", chatId);
            return "Эту кнопку нажимал другой пользователь";
        }

        if (data.decision() == AiClearCallback.Decision.CONFIRM) {
            aiService.clearChatHistory(chatId);
            messageSender.editMessage(chatId, messageId,
                    "Память обнулена, забыто " + data.forgottenCount() + " сообщений");
        } else {
            messageSender.editMessage(chatId, messageId, "Отменено");
        }
        return null;
    }

}
