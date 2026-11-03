package org.example;

import org.example.AiChat.LlmCatalog;
import org.example.Config.BotProperties;
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
import java.util.Optional;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.Settings.Settings.DEFAULT_AI_MODEL;
import static org.example.Settings.Settings.OWNER_CHAT_ID;

@Component
public class StartupNotifier {
    private static final Logger logger = LoggerFactory.getLogger(StartupNotifier.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final SettingsService settings;
    private final MessageSender sender;
    private final BotProperties properties;

    public StartupNotifier(SettingsService settings, MessageSender sender, BotProperties properties) {
        this.settings = settings;
        this.sender = sender;
        this.properties = properties;
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
                + "Хост: " + hostName() + "\n"
                + "Модель: " + modelLine() + "\n"
                + "Температура: " + temperature() + "\n"
                + "Максимум токенов: " + maxTokens()
                + adminLine();

        if (sender.sendMessage(chatId, text) != null)
            logger.info("Уведомление о запуске отправлено в чат {}", chatId);
        else
            logger.warn("Уведомление о запуске не отправлено в чат {}", chatId);
    }

    /** Модель, её поставщик и наличие ключа: при пяти провайдерах одного имени модели мало. */
    private String modelLine() {
        String configured = settings.getModel();
        Optional<LlmCatalog.Model> found = LlmCatalog.model(configured);
        if (found.isEmpty())
            return configured == null || configured.isBlank() ? DEFAULT_AI_MODEL : configured;

        LlmCatalog.Model model = found.get();
        Optional<LlmCatalog.Provider> provider = LlmCatalog.providerOf(model);
        if (provider.isEmpty())
            return model.id();

        String line = model.id() + " (" + provider.get().title() + ")";
        return properties.ai().keyFor(provider.get().id()) == null ? line + ", ключа нет" : line;
    }

    /** Ссылка на админку — только если она включена паролем. Доступна через SSH-туннель. */
    private String adminLine() {
        if (!properties.hasAdmin())
            return "";

        BotProperties.Admin admin = properties.admin();
        String bind = admin.bind();
        String host = bind == null || bind.isBlank() || "0.0.0.0".equals(bind) ? "127.0.0.1" : bind;
        int port = admin.port() == null ? 8081 : admin.port();
        return "\nАдминка: http://" + host + ":" + port + "/admin (через SSH-туннель)";
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

    private String temperature() {
        String value = settings.getSettingValue(AI_ANSWER_TEMPERATURE);
        if (value == null || value.isBlank())
            return "не задана";

        try {
            return String.valueOf(Float.parseFloat(value.trim()));
        } catch (NumberFormatException e) {
            logger.warn("Некорректная настройка {} ('{}')", AI_ANSWER_TEMPERATURE, value);
            return value.trim();
        }
    }

    private String maxTokens() {
        String value = settings.getSettingValue(AI_MAX_TOKENS_ANSWER_QUESTION);
        if (value == null || value.isBlank())
            return "не задан";
        return value.trim();
    }
}
