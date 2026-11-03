package org.example.AiChat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.example.Settings.TablesDB.PERSONA_GRUDGES_TABLE;
import static org.example.Settings.TablesDB.TG_USERS_TABLE;

/** Обиды персоны на участников чата: одна строка на человека с накопленной силой. */
@Repository
public class PersonaGrudgeRepository {
    private static final Logger logger = LoggerFactory.getLogger(PersonaGrudgeRepository.class);
    private final DataSource dataSource;

    public PersonaGrudgeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record GrudgeRow(long userId, int strength, Instant updatedAt, String nick, String name) {
    }

    public List<GrudgeRow> findForChat(long chatId) {
        String query = "SELECT g.user_id, g.strength, g.updated_at, u.user_name, u.first_name"
                + " FROM " + PERSONA_GRUDGES_TABLE + " g"
                + " LEFT JOIN " + TG_USERS_TABLE + " u ON u.user_id = g.user_id"
                + " WHERE g.chat_id = ? ORDER BY g.strength DESC";

        List<GrudgeRow> grudges = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    grudges.add(new GrudgeRow(
                            resultSet.getLong("user_id"),
                            resultSet.getInt("strength"),
                            resultSet.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
                            resultSet.getString("user_name"),
                            resultSet.getString("first_name")));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения обид чата {}: ", chatId, e);
            return List.of();
        }
        return grudges;
    }

    public void upsert(long chatId, long userId, int strength, String offence) {
        String query = "INSERT INTO " + PERSONA_GRUDGES_TABLE
                + " (chat_id, user_id, strength, last_offence, updated_at) VALUES (?, ?, ?, ?, now())"
                + " ON CONFLICT (chat_id, user_id) DO UPDATE SET"
                + " strength = EXCLUDED.strength, last_offence = EXCLUDED.last_offence, updated_at = now()";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, userId);
            statement.setInt(3, strength);
            statement.setString(4, offence);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка записи обиды чата {} на участника {}: ", chatId, userId, e);
        }
    }

    public void delete(long chatId, long userId) {
        String query = "DELETE FROM " + PERSONA_GRUDGES_TABLE + " WHERE chat_id = ? AND user_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка снятия обиды чата {} с участника {}: ", chatId, userId, e);
        }
    }
}
