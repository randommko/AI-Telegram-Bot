package org.example;

import org.example.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class TelegramBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);
    private final String botToken;

    // Зависимости
    private final MessageSender messageSender;
    private final UserChatManager userChatManager;
    private final CommandDispatcher commandDispatcher;
    private final CallbackDispatcher callbackDispatcher;
    private final ThreadPoolExecutor executor;

    public TelegramBot(BotProperties properties, DefaultBotOptions options,
                       MessageSender messageSender, UserChatManager userChatManager,
                       CommandDispatcher commandDispatcher, CallbackDispatcher callbackDispatcher) {
        super(options);
        this.botToken = properties.token();
        this.messageSender = messageSender;
        this.userChatManager = userChatManager;
        this.commandDispatcher = commandDispatcher;
        this.callbackDispatcher = callbackDispatcher;
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    }
    @Override
    public String getBotUsername() {
        return "Викторина бот";
    }
    @Override
    public String getBotToken() {
        return botToken;
    }
    @Override
    public void onUpdateReceived(Update update) {
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
