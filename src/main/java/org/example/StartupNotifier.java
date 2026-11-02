package org.example;

import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.example.Settings.Settings.OWNER_CHAT_ID;

@Component
public class StartupNotifier {
    private static final Logger logger = LoggerFactory.getLogger(StartupNotifier.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final SettingsService settings;
    private final MessageSender sender;

    public StartupNotifier(SettingsService settings, MessageSender sender) {
        this.settings = settings;
        this.sender = sender;
    }

    /**
     * Срабатывает после регистрации бота: SmartLifecycle.start() выполняется при refresh контекста,
     * то есть раньше ApplicationReadyEvent. Если бот не запустился, событие не наступит.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void notifyOwner() {
        Long chatId = ownerChatId();
        if (chatId == null)
            return;

        String text = "Бот запущен\n"
                + "Время: " + LocalDateTime.now().format(TIME_FORMAT)
                + " (" + ZoneId.systemDefault().getId() + ")\n"
                + "Хост: " + hostName();

        if (sender.sendMessage(chatId, text) != null)
            logger.info("Уведомление о запуске отправлено в чат {}", chatId);
        else
            logger.warn("Уведомление о запуске не отправлено в чат {}", chatId);
    }

    private Long ownerChatId() {
        String value = settings.getSettingValue(OWNER_CHAT_ID);
        if (value == null || value.isBlank()) {
            logger.warn("Настройка {} не задана — уведомления о запуске отключены", OWNER_CHAT_ID);
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Настройка {} содержит не число ('{}') — уведомления о запуске отключены",
                    OWNER_CHAT_ID, value);
            return null;
        }
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            logger.warn("Не удалось определить имя хоста: {}", e.toString());
            return "неизвестен";
        }
    }
}
