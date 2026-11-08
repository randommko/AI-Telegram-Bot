package org.example.bot;

import org.example.bot.aichat.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Component
public class CallbackDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(CallbackDispatcher.class);
    private final MessageSender messageSender;
    private final AiService aiService;

    public CallbackDispatcher(MessageSender messageSender, AiService aiService) {
        this.messageSender = messageSender;
        this.aiService = aiService;
    }

    public void dispatch(CallbackQuery callback) {
        String callbackData = callback.getData();
        logger.debug("Callback: {}", callbackData);

        Optional<AiClearCallback> aiClear = AiClearCallback.parse(callbackData);
        if (aiClear.isPresent()) {
            answerCallbackQuery(callback, handleAiClear(callback, aiClear.get()));
            return;
        }

        logger.warn("Неизвестный callback: {}", callbackData);
        answerCallbackQuery(callback, "Неизвестная кнопка");
    }

    private void answerCallbackQuery(CallbackQuery callback, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callback.getId())
                .text(text)
                .build();
        try {
            messageSender.getClient().execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Ошибка answerCallback: ", e);
        }
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
