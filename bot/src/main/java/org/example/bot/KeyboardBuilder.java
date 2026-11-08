package org.example.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

public class KeyboardBuilder {
    private static final Logger logger = LoggerFactory.getLogger(KeyboardBuilder.class);

    /**
     * Отправляет вопрос о сбросе памяти AI с кнопками подтверждения и отказа
     */
    public void sendAiClearQuestion(MessageSender sender, Long chatId, Long authorId, int forgottenCount) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Обнулить память AI в этом чате? Забыто будет " + forgottenCount + " сообщений")
                .replyMarkup(buildAiClearKeyboard(authorId, forgottenCount))
                .build();

        try {
            sender.getClient().execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки вопроса о сбросе памяти AI: ", e);
        }
    }

    public InlineKeyboardMarkup buildAiClearKeyboard(Long authorId, int forgottenCount) {
        InlineKeyboardButton confirm = aiClearButton("Да",
                new AiClearCallback(AiClearCallback.Decision.CONFIRM, authorId, forgottenCount));
        InlineKeyboardButton cancel = aiClearButton("Отмена",
                new AiClearCallback(AiClearCallback.Decision.CANCEL, authorId, forgottenCount));

        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(confirm, cancel)));
    }

    private InlineKeyboardButton aiClearButton(String text, AiClearCallback callback) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callback.toCallbackData())
                .build();
    }
}
