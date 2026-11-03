package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.io.File;
import java.util.List;

@Component
public class MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);

    // Бот создаётся этим классом опосредованно: TelegramBot зависит от MessageSender,
    // поэтому ссылка берётся из контекста лениво, при первой отправке.
    private final ObjectProvider<TelegramLongPollingBot> botProvider;

    public MessageSender(ObjectProvider<TelegramLongPollingBot> botProvider) {
        this.botProvider = botProvider;
    }

    public TelegramLongPollingBot getBot() {
        return botProvider.getObject();
    }

    public Integer sendMessage(Long chatId, String text) {
        SendMessage msg = SendMessage.builder().chatId(chatId).text(text).build();
        try {
            return botProvider.getObject().execute(msg).getMessageId();
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки: ", e);
            return null;
        }
    }

    /**
     * Отправляет текст целиком: длинный ответ режется на части по лимиту Telegram
     * и уходит по порядку. Сбой одной части не отменяет остальные.
     */
    public int sendLongMessage(Long chatId, String text) {
        List<String> parts = TextSplitter.split(text, TextSplitter.MAX_MESSAGE_LENGTH);
        int sent = 0;
        for (String part : parts) {
            if (sendMessage(chatId, part) != null)
                sent++;
        }
        if (parts.size() > 1)
            logger.info("Ответ в чат {} разбит на {} частей, отправлено {}", chatId, parts.size(), sent);
        return sent;
    }

    public Integer sendReplyMessage(Long chatId, Integer replyId, String text) {
        SendMessage msg = SendMessage.builder().chatId(chatId).text(text).replyToMessageId(replyId).build();
        try {
            return botProvider.getObject().execute(msg).getMessageId();
        } catch (TelegramApiException e) {
            logger.error("Ошибка reply: ", e);
            return null;
        }
    }

    public boolean sendImage(Long chatId, String caption, File image) {
        if (!image.exists()) {
            logger.error("Файл не найден: {}", image.getPath());
            return false;
        }
        SendPhoto photo = SendPhoto.builder().chatId(chatId).photo(new InputFile(image)).caption(caption).build();
        try {
            botProvider.getObject().execute(photo);
            return true;
        } catch (TelegramApiException e) {
            logger.error("Ошибка фото: ", e);
            return false;
        }
    }

    public boolean editMessage(Long chatId, Integer msgId, String text) {
        // Пустая клавиатура гасит кнопки: после ответа на вопрос их нельзя нажать повторно.
        InlineKeyboardMarkup emptyKeyboard = new InlineKeyboardMarkup();
        emptyKeyboard.setKeyboard(List.of());

        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(msgId)
                .text(text)
                .replyMarkup(emptyKeyboard)
                .build();
        try {
            botProvider.getObject().execute(edit);
            return true;
        } catch (TelegramApiException e) {
            logger.error("Ошибка edit: ", e);
            return false;
        }
    }

}
