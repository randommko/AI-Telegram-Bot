package org.example.bot;

import org.example.core.TextSplitter;
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
        return sendMessage(chatId, text, null);
    }

    /**
     * Отправка с необязательным реплаем. Если Telegram отклонил цитату (сообщение
     * удалено или недоступно), текст уходит без неё: потерять ответ хуже дубля.
     */
    public Integer sendMessage(Long chatId, String text, Integer replyToMessageId) {
        if (replyToMessageId == null)
            return send(chatId, text, null);

        Integer sent = send(chatId, text, replyToMessageId);
        if (sent != null)
            return sent;

        logger.warn("Реплай в чат {} на сообщение {} не удался: отправляю без цитаты",
                chatId, replyToMessageId);
        return send(chatId, text, null);
    }

    private Integer send(Long chatId, String text, Integer replyToMessageId) {
        var builder = SendMessage.builder().chatId(chatId).text(text);
        if (replyToMessageId != null)
            builder.replyToMessageId(replyToMessageId);
        try {
            return botProvider.getObject().execute(builder.build()).getMessageId();
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки: ", e);
            return null;
        }
    }

    /**
     * Отправляет текст целиком: длинный ответ режется на части по лимиту Telegram
     * и уходит по порядку. Реплай ставится только на первую часть.
     */
    public int sendLongMessage(Long chatId, String text) {
        return sendLongMessage(chatId, text, null);
    }

    public int sendLongMessage(Long chatId, String text, Integer replyToMessageId) {
        List<String> parts = TextSplitter.split(text, TextSplitter.MAX_MESSAGE_LENGTH);
        int sent = 0;
        for (int i = 0; i < parts.size(); i++) {
            Integer replyId = i == 0 ? replyToMessageId : null;
            if (sendMessage(chatId, parts.get(i), replyId) != null)
                sent++;
        }
        if (parts.size() > 1)
            logger.info("Ответ в чат {} разбит на {} частей, отправлено {}", chatId, parts.size(), sent);
        return sent;
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
