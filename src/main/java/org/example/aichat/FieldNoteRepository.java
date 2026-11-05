package org.example.aichat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.example.settings.TablesDB.PERSONA_FIELD_NOTES_TABLE;

/** Подсказки к параметрам персоны: у эмоций и настроек описания живут в своих таблицах. */
@Repository
public class FieldNoteRepository {
    private static final Logger logger = LoggerFactory.getLogger(FieldNoteRepository.class);

    private final DataSource dataSource;

    public FieldNoteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, String> findAll() {
        String query = "SELECT field_key, note FROM " + PERSONA_FIELD_NOTES_TABLE + " ORDER BY field_key";

        Map<String, String> notes = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                notes.put(resultSet.getString("field_key"), resultSet.getString("note"));
        } catch (Exception e) {
            logger.error("Ошибка чтения подсказок к параметрам персоны: ", e);
            return Map.of();
        }
        return notes;
    }
}
