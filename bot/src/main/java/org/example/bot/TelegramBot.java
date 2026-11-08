package org.example.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class TelegramBot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);

    // Зависимости
    private final MessageSender messageSender;
    private final UserChatManager userChatManager;
    private final CommandDispatcher commandDispatcher;
    private final CallbackDispatcher callbackDispatcher;
    private final ThreadPoolExecutor executor;

    public TelegramBot(MessageSender messageSender, UserChatManager userChatManager,
                       CommandDispatcher commandDispatcher, CallbackDispatcher callbackDispatcher) {
        this.messageSender = messageSender;
        this.userChatManager = userChatManager;
        this.commandDispatcher = commandDispatcher;
        this.callbackDispatcher = callbackDispatcher;
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }

    @Override
    public void consume(Update update) {
        CompletableFuture.runAsync(() -> processUpdate(update), executor);
        logger.debug("Активных потоков: {}", executor.getActiveCount());
    }

    private void processUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            var callback = update.getCallbackQuery();
            userChatManager.checkUser(callback.getFrom());
            userChatManager.checkChat(callback.getMessage().getChat());
            callbackDispatcher.dispatch(callback);
            return;
        }

        Message message = update.getMessage();
        if (message == null || message.getText() == null) {
            logger.debug("Пустое или не-текстовое сообщение");
            return;
        }

        logger.debug("Сообщение из {}: {}", message.getChat().getId(), message.getText());

        userChatManager.checkUser(message.getFrom());
        userChatManager.checkChat(message.getChat());

        commandDispatcher.dispatch(update);
    }
}
