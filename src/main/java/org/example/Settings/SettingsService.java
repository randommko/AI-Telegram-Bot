package org.example.Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.example.Settings.TablesDB.SETTINGS_TABLE;


@Service
public class SettingsService {
    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private final DataSource dataSource;

    public SettingsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getSettingValue(String key) {
        String query = "SELECT value " +
                "FROM " + SETTINGS_TABLE +
                " WHERE setting_key = ? ";

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, key);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next())
                return resultSet.getString("value");
        } catch (Exception e) {
            logger.error("Ошибка получения настройки из БД: {}", String.valueOf(e));
            return null;
        }
        return null;
    }
}
