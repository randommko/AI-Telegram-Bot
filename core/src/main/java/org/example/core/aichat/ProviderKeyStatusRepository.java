package org.example.core.aichat;

import org.example.core.settings.TablesDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Админка читает только признак наличия ключа: сами ключи остаются в окружении бота. */
@Repository
public class ProviderKeyStatusRepository {
    private static final Logger logger = LoggerFactory.getLogger(ProviderKeyStatusRepository.class);

    private final DataSource dataSource;

    public ProviderKeyStatusRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean hasKey(String providerId) {
        String query = "SELECT has_key FROM " + TablesDB.PROVIDER_KEY_STATUS_TABLE
                + " WHERE provider_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, providerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean("has_key");
            }
        } catch (Exception e) {
            logger.warn("Не удалось прочитать статус ключа {}: {}", providerId, e.toString());
            return false;
        }
    }
}
