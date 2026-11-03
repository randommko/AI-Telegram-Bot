package org.example.Admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.example.Settings.TablesDB.ADMIN_CHANGES_TABLE;

/**
 * Журнал изменений админки: кто, когда и какие поля изменил.
 * Запись идёт в соединение вызывающего, поэтому изменение и его след живут в одной транзакции.
 */
@Component
public class ChangeJournal {
    public static final String PERSONA = "persona";
    public static final String BINDING = "binding";
    public static final String MEMORY = "memory";
    public static final String ACCOUNT = "account";

    /** Сколько последних записей хранится по одной персоне. */
    static final int KEEP_PER_PERSONA = 20;
    private static final Logger logger = LoggerFactory.getLogger(ChangeJournal.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChangeJournal(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Entry(long id, OffsetDateTime at, String actorLogin, String entity, Integer entityId,
                        Long chatId, Integer personaId, Map<String, Object> before, Map<String, Object> after) {

        /** Название раздела для страницы истории. */
        public String entityTitle() {
            return switch (entity) {
                case PERSONA -> "персона";
                case BINDING -> "привязка к чату";
                case MEMORY -> "воспоминание";
                case ACCOUNT -> "учётка";
                default -> entity;
            };
        }

        public boolean restorable() {
            return PERSONA.equals(entity);
        }

        /** Изменённые поля одной строкой: журнал не показывает значения целиком. */
        public String fieldsTitle() {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            if (before != null)
                keys.addAll(before.keySet());
            if (after != null)
                keys.addAll(after.keySet());
            return keys.isEmpty() ? "без изменений" : String.join(", ", keys);
        }
    }

    /** Запись об изменении; пустая пара значений не пишется — значит, ничего не менялось. */
    public void record(Connection connection, Actor actor, String entity, Integer entityId,
                       Long chatId, Integer personaId,
                       Map<String, Object> before, Map<String, Object> after) throws SQLException {
        if (isEmpty(before) && isEmpty(after))
            return;

        String query = "INSERT INTO " + ADMIN_CHANGES_TABLE
                + " (actor_user_id, actor_login, entity, entity_id, chat_id, persona_id, before, after)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setObject(1, actor.tgUserId(), java.sql.Types.BIGINT);
            statement.setString(2, actor.login());
            statement.setString(3, entity);
            statement.setObject(4, entityId, java.sql.Types.INTEGER);
            statement.setObject(5, chatId, java.sql.Types.BIGINT);
            statement.setObject(6, personaId, java.sql.Types.INTEGER);
            statement.setObject(7, json(before));
            statement.setObject(8, json(after));
            statement.executeUpdate();
        }

        if (personaId != null)
            prune(connection, personaId);
    }

    /** История по персоне: и правки карточки, и смены привязки. */
    public List<Entry> historyOfPersona(int personaId, int limit) {
        return read("SELECT * FROM " + ADMIN_CHANGES_TABLE
                + " WHERE persona_id = ? ORDER BY at DESC, id DESC LIMIT ?", personaId, limit);
    }

    public List<Entry> historyOfMemory(int memoryId, int limit) {
        return read("SELECT * FROM " + ADMIN_CHANGES_TABLE
                + " WHERE entity = ? AND entity_id = ? ORDER BY at DESC, id DESC LIMIT ?",
                MEMORY, memoryId, limit);
    }

    public Optional<Entry> find(long changeId) {
        return read("SELECT * FROM " + ADMIN_CHANGES_TABLE + " WHERE id = ?", changeId).stream().findFirst();
    }

    /** Обрезка до последних записей по персоне: объём ограничен способом записи. */
    private void prune(Connection connection, int personaId) throws SQLException {
        String query = "DELETE FROM " + ADMIN_CHANGES_TABLE + " WHERE persona_id = ? AND id NOT IN ("
                + "SELECT id FROM " + ADMIN_CHANGES_TABLE + " WHERE persona_id = ?"
                + " ORDER BY at DESC, id DESC LIMIT " + KEEP_PER_PERSONA + ")";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            statement.setInt(2, personaId);
            statement.executeUpdate();
        }
    }

    private List<Entry> read(String query, Object... params) {
        List<Entry> entries = new java.util.ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++)
                statement.setObject(i + 1, params[i]);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new Entry(
                            resultSet.getLong("id"),
                            resultSet.getObject("at", OffsetDateTime.class),
                            resultSet.getString("actor_login"),
                            resultSet.getString("entity"),
                            resultSet.getObject("entity_id", Integer.class),
                            resultSet.getObject("chat_id", Long.class),
                            resultSet.getObject("persona_id", Integer.class),
                            json(resultSet.getString("before")),
                            json(resultSet.getString("after"))));
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения журнала изменений: ", e);
        }
        return entries;
    }

    private PGobject json(Map<String, Object> value) throws SQLException {
        PGobject object = new PGobject();
        object.setType("jsonb");
        try {
            object.setValue(isEmpty(value) ? null : objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new SQLException("Не удалось собрать JSON журнала изменений", e);
        }
        return object;
    }

    private Map<String, Object> json(String raw) {
        if (raw == null || raw.isBlank())
            return Map.of();
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception e) {
            logger.warn("Некорректный JSON в журнале изменений: {}", raw);
            return Map.of();
        }
    }

    private static boolean isEmpty(Map<String, Object> value) {
        return value == null || value.isEmpty();
    }

    /** Пара «до и после» по изменившимся полям; пусто, если значения совпадают. */
    public static void diff(Map<String, Object> before, Map<String, Object> after,
                     String key, Object oldValue, Object newValue) {
        if (java.util.Objects.equals(oldValue, newValue))
            return;
        if (before != null)
            before.put(key, oldValue);
        if (after != null)
            after.put(key, newValue);
    }

    static Map<String, Object> fields() {
        return new LinkedHashMap<>();
    }
}
