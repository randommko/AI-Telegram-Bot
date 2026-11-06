package org.example.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.example.core.settings.TablesDB.CHAT_PERSONAS_TABLE;
import static org.example.core.settings.TablesDB.MODERATOR_PERSONAS_TABLE;
import static org.example.core.settings.TablesDB.PERSONAS_TABLE;

/**
 * Доступ модератора к персонам: второй источник правки после авторства.
 * Чат в этой таблице не участвует — он даёт только просмотр.
 */
@Repository
public class ModeratorPersonaRepository {
    private static final Logger logger = LoggerFactory.getLogger(ModeratorPersonaRepository.class);
    private final DataSource dataSource;

    public ModeratorPersonaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Откуда у модератора доступ: создал сам, выдал владелец или персона его чата. */
    public enum Source {
        CREATED("создал"), GRANTED("выдан"), CHAT("чат");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Отозвать можно только выдачу: авторство и чат от кнопки не зависят. */
        public boolean revocable() {
            return this == GRANTED;
        }
    }

    public record PersonaAccessRow(int personaId, String name, Source source) {
    }

    public List<PersonaAccessRow> accessOf(long tgUserId, List<Long> chatIds) {
        String query = "SELECT p.id, p.name,"
                + " CASE WHEN p.created_by = ? THEN 'created'"
                + " WHEN EXISTS (SELECT 1 FROM " + MODERATOR_PERSONAS_TABLE + " mp"
                + " WHERE mp.persona_id = p.id AND mp.tg_user_id = ?) THEN 'granted'"
                + " ELSE 'chat' END AS source"
                + " FROM " + PERSONAS_TABLE + " p"
                + " WHERE p.created_by = ?"
                + " OR EXISTS (SELECT 1 FROM " + MODERATOR_PERSONAS_TABLE + " mp"
                + " WHERE mp.persona_id = p.id AND mp.tg_user_id = ?)"
                + " OR EXISTS (SELECT 1 FROM " + CHAT_PERSONAS_TABLE + " cp"
                + " WHERE cp.persona_id = p.id AND cp.chat_id = ANY(?))"
                + " ORDER BY lower(p.name)";

        List<PersonaAccessRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            statement.setLong(2, tgUserId);
            statement.setLong(3, tgUserId);
            statement.setLong(4, tgUserId);
            statement.setArray(5, array(connection, chatIds));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    rows.add(new PersonaAccessRow(
                            resultSet.getInt("id"),
                            resultSet.getString("name"),
                            source(resultSet.getString("source"))));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения персон модератора {}: ", tgUserId, e);
        }
        return rows;
    }

    public void grant(long tgUserId, int personaId, Long grantedBy) {
        String query = "INSERT INTO " + MODERATOR_PERSONAS_TABLE
                + " (tg_user_id, persona_id, granted_by) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            statement.setInt(2, personaId);
            statement.setObject(3, grantedBy, java.sql.Types.BIGINT);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка выдачи персоны {} модератору {}: ", personaId, tgUserId, e);
        }
    }

    public void revoke(long tgUserId, int personaId) {
        String query = "DELETE FROM " + MODERATOR_PERSONAS_TABLE
                + " WHERE tg_user_id = ? AND persona_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            statement.setInt(2, personaId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка отзыва персоны {} у модератора {}: ", personaId, tgUserId, e);
        }
    }

    private Source source(String value) {
        if ("created".equals(value))
            return Source.CREATED;
        if ("granted".equals(value))
            return Source.GRANTED;
        return Source.CHAT;
    }

    private Array array(Connection connection, List<Long> ids) throws java.sql.SQLException {
        return connection.createArrayOf("bigint", ids.toArray());
    }
}
