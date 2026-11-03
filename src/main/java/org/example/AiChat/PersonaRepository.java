package org.example.AiChat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.example.Settings.TablesDB.AI_MESSAGES_TABLE;
import static org.example.Settings.TablesDB.CHAT_PERSONAS_TABLE;
import static org.example.Settings.TablesDB.PERSONAS_TABLE;
import static org.example.Settings.TablesDB.PERSONA_ALIASES_TABLE;
import static org.example.Settings.TablesDB.PERSONA_MEMORIES_TABLE;
import static org.example.Settings.TablesDB.TG_CHATS_TABLE;

@Repository
public class PersonaRepository {
    private static final Logger logger = LoggerFactory.getLogger(PersonaRepository.class);
    private final DataSource dataSource;
    private final EmotionRepository emotions;

    public PersonaRepository(DataSource dataSource, EmotionRepository emotions) {
        this.dataSource = dataSource;
        this.emotions = emotions;
    }

    public Optional<Persona> findForChat(long chatId) {
        String query = "SELECT p.id, p.name, p.prompt FROM " + CHAT_PERSONAS_TABLE + " cp" +
                " JOIN " + PERSONAS_TABLE + " p ON p.id = cp.persona_id WHERE cp.chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(new Persona(
                            resultSet.getInt("id"),
                            resultSet.getString("name"),
                            resultSet.getString("prompt")));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения персоны чата {}: ", chatId, e);
        }
        return Optional.empty();
    }

    /** Параметры ответа персоны этого чата: реплики, пауза, срыв и время последнего срыва. */
    public Optional<PersonaSettings> findSettingsForChat(long chatId) {
        String query = "SELECT p.id, p.max_replies, p.reply_delay_ms, p.aggression_threshold,"
                + " p.hysteria_replies, p.hysteria_cooldown_hours, p.last_hysteria_at"
                + " FROM " + CHAT_PERSONAS_TABLE + " cp"
                + " JOIN " + PERSONAS_TABLE + " p ON p.id = cp.persona_id WHERE cp.chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    var lastHysteria = resultSet.getObject("last_hysteria_at", java.time.OffsetDateTime.class);
                    return Optional.of(new PersonaSettings(
                            resultSet.getInt("id"),
                            resultSet.getInt("max_replies"),
                            resultSet.getInt("reply_delay_ms"),
                            resultSet.getInt("aggression_threshold"),
                            resultSet.getInt("hysteria_replies"),
                            resultSet.getInt("hysteria_cooldown_hours"),
                            lastHysteria == null ? null : lastHysteria.toInstant()));
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения параметров персоны чата {}: ", chatId, e);
        }
        return Optional.empty();
    }

    /** Время последнего срыва: пауза принадлежит персоне, а не чату. */
    public void updateLastHysteriaAt(int personaId, java.time.Instant at) {
        String query = "UPDATE " + PERSONAS_TABLE + " SET last_hysteria_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setObject(1, java.time.OffsetDateTime.ofInstant(at, java.time.ZoneOffset.UTC));
            statement.setInt(2, personaId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка записи времени срыва персоны {}: ", personaId, e);
        }
    }

    /** Алиасы персоны этого чата, от длинных к коротким. */
    public List<String> findAliasesForChat(long chatId) {
        String query = "SELECT a.alias FROM " + PERSONA_ALIASES_TABLE + " a" +
                " JOIN " + CHAT_PERSONAS_TABLE + " cp ON cp.persona_id = a.persona_id" +
                " WHERE cp.chat_id = ? ORDER BY length(a.alias) DESC";

        List<String> aliases = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    aliases.add(resultSet.getString("alias"));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения алиасов персоны чата {}: ", chatId, e);
            return Collections.emptyList();
        }
        return aliases;
    }

    /** Все чаты, у которых есть персона: единица обхода фонового анализа. */
    public List<ChatPersona> findAssignedChats() {
        String query = "SELECT chat_id, persona_id FROM " + CHAT_PERSONAS_TABLE + " ORDER BY chat_id";

        List<ChatPersona> chats = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                chats.add(new ChatPersona(resultSet.getLong("chat_id"), resultSet.getInt("persona_id")));
        } catch (Exception e) {
            logger.error("Ошибка чтения привязок персон к чатам: ", e);
            return Collections.emptyList();
        }
        return chats;
    }

    /** Строки списка персон с числом алиасов, воспоминаний и привязанными чатами. */
    public List<PersonaSummary> findAllWithCounts() {
        String query = "SELECT p.id, p.name,"
                + " (SELECT count(*) FROM " + PERSONA_ALIASES_TABLE + " a WHERE a.persona_id = p.id),"
                + " (SELECT count(*) FROM " + PERSONA_MEMORIES_TABLE + " m WHERE m.persona_id = p.id),"
                + " (SELECT string_agg(cp.chat_id::text, ', ' ORDER BY cp.chat_id) FROM " + CHAT_PERSONAS_TABLE
                + " cp WHERE cp.persona_id = p.id)"
                + " FROM " + PERSONAS_TABLE + " p ORDER BY lower(p.name)";

        List<PersonaSummary> personas = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                personas.add(new PersonaSummary(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getInt(3),
                        resultSet.getInt(4),
                        resultSet.getString(5)));
        } catch (Exception e) {
            logger.error("Ошибка чтения списка персон: ", e);
            return Collections.emptyList();
        }
        return personas;
    }

    public Optional<PersonaDetails> findDetails(int id) {
        String query = "SELECT * FROM " + PERSONAS_TABLE + " WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(details(resultSet, aliases(connection, id), emotionBaseValues(id)));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения персоны {}: ", id, e);
        }
        return Optional.empty();
    }

    /** Создание персоны, её алиасов и базовых значений эмоций одной транзакцией. */
    public void create(PersonaDetails details) {
        String query = "INSERT INTO " + PERSONAS_TABLE + " (name, prompt, aggression_threshold, hysteria_replies,"
                + " hysteria_cooldown_hours, max_replies, reply_delay_ms, initiative_enabled,"
                + " initiative_cooldown_messages) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + " RETURNING id";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                bindPersona(statement, details);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    int personaId = resultSet.getInt("id");
                    replaceAliases(connection, personaId, details.aliases());
                    emotions.saveBaseValues(connection, personaId, details.emotionBaseValues());
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.error("Ошибка создания персоны {}: ", details.name(), e);
        }
    }

    /** Обновление карточки, полная замена алиасов и базовых значений эмоций одной транзакцией. */
    public void update(PersonaDetails details) {
        String query = "UPDATE " + PERSONAS_TABLE + " SET name = ?, prompt = ?, aggression_threshold = ?,"
                + " hysteria_replies = ?, hysteria_cooldown_hours = ?, max_replies = ?, reply_delay_ms = ?,"
                + " initiative_enabled = ?, initiative_cooldown_messages = ?, updated_at = now() WHERE id = ?";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                bindPersona(statement, details);
                statement.setInt(10, details.id());
                statement.executeUpdate();
                replaceAliases(connection, details.id(), details.aliases());
                emotions.saveBaseValues(connection, details.id(), details.emotionBaseValues());
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            logger.error("Ошибка обновления персоны {}: ", details.id(), e);
        }
    }

    public void delete(int id) {
        String query = "DELETE FROM " + PERSONAS_TABLE + " WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка удаления персоны {}: ", id, e);
        }
    }

    /** Имя занято другой персоной? Регистр не важен. */
    public boolean nameTaken(String name, Integer exceptId) {
        String query = "SELECT 1 FROM " + PERSONAS_TABLE + " WHERE lower(name) = lower(?)"
                + " AND (? IS NULL OR id <> ?) LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            if (exceptId == null)
                statement.setNull(2, java.sql.Types.INTEGER);
            else
                statement.setInt(2, exceptId);
            if (exceptId == null)
                statement.setNull(3, java.sql.Types.INTEGER);
            else
                statement.setInt(3, exceptId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            logger.error("Ошибка проверки имени персоны {}: ", name, e);
        }
        return false;
    }

    /** Персона, которой уже принадлежит алиас, если это не она сама. */
    public Optional<Integer> aliasOwner(String alias, Integer exceptPersonaId) {
        String query = "SELECT persona_id FROM " + PERSONA_ALIASES_TABLE + " WHERE lower(alias) = lower(?)"
                + " AND (? IS NULL OR persona_id <> ?) LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, alias);
            if (exceptPersonaId == null)
                statement.setNull(2, java.sql.Types.INTEGER);
            else
                statement.setInt(2, exceptPersonaId);
            if (exceptPersonaId == null)
                statement.setNull(3, java.sql.Types.INTEGER);
            else
                statement.setInt(3, exceptPersonaId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(resultSet.getInt("persona_id"));
            }
        } catch (Exception e) {
            logger.error("Ошибка проверки алиаса {}: ", alias, e);
        }
        return Optional.empty();
    }

    public List<Long> chatsOf(int personaId) {
        String query = "SELECT chat_id FROM " + CHAT_PERSONAS_TABLE + " WHERE persona_id = ? ORDER BY chat_id";

        List<Long> chats = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    chats.add(resultSet.getLong("chat_id"));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения чатов персоны {}: ", personaId, e);
        }
        return chats;
    }

    private List<String> aliases(Connection connection, int personaId) throws Exception {
        String query = "SELECT alias FROM " + PERSONA_ALIASES_TABLE + " WHERE persona_id = ? ORDER BY lower(alias)";

        List<String> aliases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    aliases.add(resultSet.getString("alias"));
            }
        }
        return aliases;
    }

    private void replaceAliases(Connection connection, int personaId, List<String> aliases) throws Exception {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + PERSONA_ALIASES_TABLE + " WHERE persona_id = ?")) {
            delete.setInt(1, personaId);
            delete.executeUpdate();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + PERSONA_ALIASES_TABLE + " (persona_id, alias) VALUES (?, ?)")) {
            for (String alias : aliases) {
                insert.setInt(1, personaId);
                insert.setString(2, alias);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void bindPersona(PreparedStatement statement, PersonaDetails details) throws Exception {
        statement.setString(1, details.name());
        statement.setString(2, details.prompt());
        statement.setInt(3, details.aggressionThreshold());
        statement.setInt(4, details.hysteriaReplies());
        statement.setInt(5, details.hysteriaCooldownHours());
        statement.setInt(6, details.maxReplies());
        statement.setInt(7, details.replyDelayMs());
        statement.setBoolean(8, details.initiativeEnabled());
        statement.setInt(9, details.initiativeCooldownMessages());
    }

    /** Базовые значения эмоций персоны; недостающие строки создаются из умолчаний справочника. */
    private Map<String, Integer> emotionBaseValues(int personaId) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (PersonaEmotion emotion : emotions.findForPersona(personaId))
            values.put(emotion.emotionCode(), emotion.baseValue());
        return values;
    }

    private PersonaDetails details(ResultSet resultSet, List<String> aliases,
                                   Map<String, Integer> emotionBaseValues) throws Exception {
        return new PersonaDetails(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("prompt"),
                aliases,
                emotionBaseValues,
                resultSet.getInt("aggression_threshold"),
                resultSet.getInt("hysteria_replies"),
                resultSet.getInt("hysteria_cooldown_hours"),
                resultSet.getInt("max_replies"),
                resultSet.getInt("reply_delay_ms"),
                resultSet.getBoolean("initiative_enabled"),
                resultSet.getInt("initiative_cooldown_messages"));
    }

    /** Чат с текущей персоной — строка страницы привязки. */
    public record ChatSummary(long chatId, String title, Integer personaId, String personaName,
                              java.time.OffsetDateTime assignedAt) {
    }

    /** Все известные чаты и их персоны, включая чаты без привязки. */
    public List<ChatSummary> findAllChats() {
        String query = "SELECT c.chat_id, c.chat_title, cp.persona_id, p.name, cp.assigned_at"
                + " FROM " + TG_CHATS_TABLE + " c"
                + " LEFT JOIN " + CHAT_PERSONAS_TABLE + " cp ON cp.chat_id = c.chat_id"
                + " LEFT JOIN " + PERSONAS_TABLE + " p ON p.id = cp.persona_id"
                + " ORDER BY c.chat_id";

        List<ChatSummary> chats = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Integer personaId = resultSet.getObject("persona_id", Integer.class);
                chats.add(new ChatSummary(
                        resultSet.getLong("chat_id"),
                        resultSet.getString("chat_title"),
                        personaId,
                        resultSet.getString("name"),
                        resultSet.getObject("assigned_at", java.time.OffsetDateTime.class)));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения чатов с персонами: ", e);
        }
        return chats;
    }

    /**
     * Смена персоны одной транзакцией: привязка, очистка истории и сообщение о входе.
     * Отметка анализа и счётчик сбросов не трогаются. personaId = null снимает привязку.
     */
    public void switchPersona(long chatId, Integer personaId, String joinMessage) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (personaId == null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM " + CHAT_PERSONAS_TABLE + " WHERE chat_id = ?")) {
                        statement.setLong(1, chatId);
                        statement.executeUpdate();
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO " + CHAT_PERSONAS_TABLE + " (chat_id, persona_id, assigned_at)"
                                    + " VALUES (?, ?, now())"
                                    + " ON CONFLICT (chat_id) DO UPDATE SET persona_id = EXCLUDED.persona_id,"
                                    + " assigned_at = now()")) {
                        statement.setLong(1, chatId);
                        statement.setInt(2, personaId);
                        statement.executeUpdate();
                    }
                }

                if (joinMessage != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM " + AI_MESSAGES_TABLE + " WHERE chat_id = ?")) {
                        statement.setLong(1, chatId);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO " + AI_MESSAGES_TABLE + " (chat_id, role, content)"
                                    + " VALUES (?, 'assistant', ?)")) {
                        statement.setLong(1, chatId);
                        statement.setString(2, joinMessage);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка смены персоны в чате {}: ", chatId, e);
        }
    }
}
