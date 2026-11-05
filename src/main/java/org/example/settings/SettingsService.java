package org.example.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.example.settings.Settings.AI_MODEL_KEY;
import static org.example.settings.Settings.AI_ROUTE_KEY;
import static org.example.settings.Settings.DEFAULT_AI_MODEL;
import static org.example.settings.TablesDB.SETTINGS_TABLE;


@Service
public class SettingsService {
    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private final DataSource dataSource;

    public SettingsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Строка таблицы настроек для страницы админки. */
    public record Setting(String key, String value, String description) {
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

    /** Целое из настройки; мусор и пустое значение дают дефолт с предупреждением в лог. */
    public int getInt(String key, int defaultValue) {
        String configured = getSettingValue(key);
        if (configured == null || configured.isBlank())
            return defaultValue;

        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            logger.warn("Некорректное целое в настройке {} ('{}'): используется {}", key, configured, defaultValue);
            return defaultValue;
        }
    }

    /** Дробное из настройки; мусор и пустое значение дают дефолт с предупреждением в лог. */
    public float getFloat(String key, float defaultValue) {
        String configured = getSettingValue(key);
        if (configured == null || configured.isBlank())
            return defaultValue;

        try {
            return Float.parseFloat(configured.trim());
        } catch (NumberFormatException e) {
            logger.warn("Некорректное число в настройке {} ('{}'): используется {}", key, configured, defaultValue);
            return defaultValue;
        }
    }

    /** Флажок: включён, если строки нет или она пуста; выключает только явное false. */
    public boolean getBoolean(String key, boolean defaultValue) {
        String configured = getSettingValue(key);
        if (configured == null || configured.isBlank())
            return defaultValue;

        String value = configured.trim();
        if ("true".equalsIgnoreCase(value))
            return true;
        if ("false".equalsIgnoreCase(value))
            return false;

        logger.warn("Некорректное значение флажка {} ('{}'): используется {}", key, configured, defaultValue);
        return defaultValue;
    }

    /** Модель для запросов к ИИ: читается на каждый запрос, поэтому правка работает без рестарта. */
    public String getModel() {
        String configured = getSettingValue(AI_MODEL_KEY);
        if (configured == null || configured.isBlank())
            return DEFAULT_AI_MODEL;
        return configured.trim();
    }

    /** Дверь к модели — провайдер из каталога; пустая строка означает прежнее поведение. */
    public String getRoute() {
        String configured = getSettingValue(AI_ROUTE_KEY);
        return configured == null ? "" : configured.trim();
    }

    public List<Setting> listAll() {
        String query = "SELECT setting_key, value, description FROM " + SETTINGS_TABLE + " ORDER BY setting_key";
        List<Setting> settings = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                settings.add(new Setting(
                        resultSet.getString("setting_key"),
                        resultSet.getString("value"),
                        resultSet.getString("description")));
        } catch (Exception e) {
            logger.error("Ошибка чтения настроек из БД: {}", String.valueOf(e));
            return Collections.emptyList();
        }
        return settings;
    }

    public void save(String key, String value) {
        String query = "INSERT INTO " + SETTINGS_TABLE + " (setting_key, value) VALUES (?, ?)" +
                " ON CONFLICT (setting_key) DO UPDATE SET value = EXCLUDED.value";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка сохранения настройки {}: {}", key, String.valueOf(e));
        }
    }
}
