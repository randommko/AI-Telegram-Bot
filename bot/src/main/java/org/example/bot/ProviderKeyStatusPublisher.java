package org.example.bot;

import org.example.core.aichat.LlmCatalog;
import org.example.core.config.BotProperties;
import org.example.core.settings.TablesDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Публикует в базу только признак наличия ключа: сами ключи админке не нужны
 * и остаются в окружении бота.
 */
@Component
public class ProviderKeyStatusPublisher {
    private static final Logger logger = LoggerFactory.getLogger(ProviderKeyStatusPublisher.class);

    private final DataSource dataSource;
    private final BotProperties properties;

    public ProviderKeyStatusPublisher(DataSource dataSource, BotProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    public record Status(String providerId, boolean hasKey) {
    }

    public List<Status> statuses() {
        return LlmCatalog.providers().stream()
                .map(provider -> new Status(provider.id(), properties.ai().keyFor(provider.id()) != null))
                .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publish() {
        String query = "INSERT INTO " + TablesDB.PROVIDER_KEY_STATUS_TABLE
                + " (provider_id, has_key, updated_at) VALUES (?, ?, now())"
                + " ON CONFLICT (provider_id) DO UPDATE"
                + " SET has_key = EXCLUDED.has_key, updated_at = now()";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            for (Status status : statuses()) {
                statement.setString(1, status.providerId());
                statement.setBoolean(2, status.hasKey());
                statement.addBatch();
            }
            statement.executeBatch();
            logger.info("Статус ключей провайдеров опубликован: {} записей", statuses().size());
        } catch (Exception e) {
            logger.error("Не удалось опубликовать статус ключей провайдеров: ", e);
        }
    }
}
