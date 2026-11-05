package org.example.AiChat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.example.Settings.TablesDB.AI_MESSAGES_TABLE;
import static org.example.Settings.TablesDB.MEMORY_ANALYSIS_STATE_TABLE;
import static org.example.Settings.TablesDB.MEMORY_CATEGORIES_TABLE;
import static org.example.Settings.TablesDB.MEMORY_PEOPLE_TABLE;
import static org.example.Settings.TablesDB.PERSONA_MEMORIES_TABLE;
import static org.example.Settings.TablesDB.TG_USERS_TABLE;

@Repository
public class MemoryRepository {
    private static final Logger logger = LoggerFactory.getLogger(MemoryRepository.class);
    private static final String DEFAULT_CATEGORY = "другое";

    private static final String MEMORY_SELECT =
            "SELECT m.id, m.category, m.content, m.importance, m.is_core, m.event_date, m.source, m.updated_at," +
                    " array_agg(coalesce(u.user_name, u.first_name) ORDER BY coalesce(u.user_name, u.first_name))" +
                    " FILTER (WHERE u.user_id IS NOT NULL) AS people" +
                    " FROM " + PERSONA_MEMORIES_TABLE + " m" +
                    " LEFT JOIN " + MEMORY_PEOPLE_TABLE + " mp ON mp.memory_id = m.id" +
                    " LEFT JOIN " + TG_USERS_TABLE + " u ON u.user_id = mp.user_id" +
                    " WHERE m.persona_id = ? AND m.deleted_at IS NULL ";

    private final DataSource dataSource;

    public MemoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record MessageRow(long id, String content, Instant createdAt, MessageAuthor author) {

        public MessageRow(long id, String content) {
            this(id, content, Instant.EPOCH, MessageAuthor.none());
        }

        public MessageRow(long id, String content, Instant createdAt) {
            this(id, content, createdAt, MessageAuthor.none());
        }
    }

    public record WindowInfo(long minId, int count) {
    }

    /** Фильтры страницы воспоминаний; null или false означает «не ограничивать». */
    public record MemoryFilter(Integer personaId, String category, Long personId, String text,
                               boolean coreOnly, String sort, int page, int pageSize,
                               List<Integer> visiblePersonas) {

        public MemoryFilter(Integer personaId, String category, Long personId, String text,
                            boolean coreOnly, String sort, int page, int pageSize) {
            this(personaId, category, personId, text, coreOnly, sort, page, pageSize, null);
        }
    }

    /** Страница результатов поиска вместе с общим числом найденных записей. */
    public record MemoryPage(List<MemoryRecord> items, int total, int page, int pageSize) {

        public int totalPages() {
            return Math.max(1, (int) Math.ceil((double) total / pageSize));
        }

        public boolean hasPrev() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages();
        }
    }

    /** Категория из справочника для выпадающего списка. */
    public record CategoryRef(String code, String title) {
    }

    /** Человек из tg_users для мультивыбора. */
    public record PersonRef(long userId, String title) {
    }

    /** Воспоминание с идентификаторами людей — для формы редактирования. */
    public record MemoryDetails(int id, int personaId, String category, String content, int importance,
                                boolean core, LocalDate eventDate, String source, List<Long> personIds) {
    }

    public long watermark(long chatId) {
        String query = "SELECT last_message_id FROM " + MEMORY_ANALYSIS_STATE_TABLE + " WHERE chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getLong("last_message_id");
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения отметки анализа в чате {}: ", chatId, e);
        }
        return 0;
    }

    public int countNewMessages(long chatId, long watermark) {
        String query = "SELECT count(*) FROM " + AI_MESSAGES_TABLE + " WHERE chat_id = ? AND id > ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, watermark);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getInt(1);
            }
        } catch (Exception e) {
            logger.error("Ошибка подсчёта новых сообщений в чате {}: ", chatId, e);
        }
        return 0;
    }

    public WindowInfo window(long chatId) {
        String query = "SELECT coalesce(min(id), 0) AS min_id, count(*) AS total FROM " + AI_MESSAGES_TABLE +
                " WHERE chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return new WindowInfo(resultSet.getLong("min_id"), resultSet.getInt("total"));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения окна истории в чате {}: ", chatId, e);
        }
        return new WindowInfo(0, 0);
    }

    public List<MessageRow> findNewMessages(long chatId, long watermark) {
        String query = "SELECT id, content, created_at, author_user_id, author_nick, author_name FROM "
                + AI_MESSAGES_TABLE + " WHERE chat_id = ? AND id > ? ORDER BY id";

        List<MessageRow> messages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, watermark);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    messages.add(new MessageRow(
                            resultSet.getLong("id"),
                            resultSet.getString("content"),
                            resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                            new MessageAuthor(
                                    resultSet.getObject("author_user_id", Long.class),
                                    resultSet.getString("author_nick"),
                                    resultSet.getString("author_name"))));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения новых сообщений в чате {}: ", chatId, e);
            return List.of();
        }
        return messages;
    }

    public List<MemoryRecord> findByPersona(int personaId) {
        return find(MEMORY_SELECT + "GROUP BY m.id ORDER BY m.is_core DESC, m.importance DESC", personaId);
    }

    public List<MemoryRecord> findCore(int personaId) {
        return find(MEMORY_SELECT + "AND m.is_core GROUP BY m.id ORDER BY m.category, m.importance DESC", personaId);
    }

    public List<MemoryRecord> findTopRegular(int personaId, int limit) {
        String query = MEMORY_SELECT + "AND NOT m.is_core GROUP BY m.id" +
                " ORDER BY m.importance DESC, m.event_date DESC NULLS LAST, m.id DESC LIMIT ?";
        return find(query, personaId, limit);
    }

    public Set<String> categories() {
        String query = "SELECT code FROM " + MEMORY_CATEGORIES_TABLE;
        Set<String> categories = new HashSet<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                categories.add(resultSet.getString("code"));
        } catch (Exception e) {
            logger.error("Ошибка чтения справочника категорий памяти: ", e);
        }
        return categories;
    }

    /** Ники и имена участников чата в нижнем регистре — для привязки людей к воспоминаниям. */
    public Map<String, Long> userIds(Collection<String> names) {
        if (names == null || names.isEmpty())
            return Map.of();

        String placeholders = String.join(", ", names.stream().map(name -> "?").toList());
        // В tg_users ник хранится с ведущим «@», а модель возвращает его без него —
        // сравниваем обе стороны без собачки, иначе все связи людей теряются.
        String query = "SELECT user_id, user_name, first_name FROM " + TG_USERS_TABLE +
                " WHERE lower(replace(user_name, '@', '')) IN (" + placeholders + ")" +
                " OR lower(replace(first_name, '@', '')) IN (" + placeholders + ")";

        Map<String, Long> users = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (String ignored : names)
                statement.setString(index++, ignored.toLowerCase());
            for (String ignored : names)
                statement.setString(index++, ignored.toLowerCase());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long userId = resultSet.getLong("user_id");
                    putUser(users, resultSet.getString("user_name"), userId);
                    putUser(users, resultSet.getString("first_name"), userId);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка поиска людей для воспоминаний: ", e);
        }
        return users;
    }

    /**
     * Пишет изменения памяти и отметку анализа одной транзакцией: при сбое
     * память и отметка остаются нетронутыми.
     */
    public void apply(long chatId, int personaId, MemoryExtraction extraction, long watermark,
                      Set<String> categories, Map<String, Long> users) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (MemoryDraft draft : extraction.add())
                    insert(connection, personaId, draft, categories, users);
                for (MemoryDraft draft : extraction.update())
                    update(connection, personaId, draft, categories, users);
                for (Long id : extraction.delete())
                    delete(connection, personaId, id);

                saveWatermark(connection, chatId, watermark);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private List<MemoryRecord> find(String query, int personaId, Integer... limit) {
        List<MemoryRecord> memories = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            if (limit.length > 0 && limit[0] != null)
                statement.setInt(2, limit[0]);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    memories.add(toMemory(resultSet));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения памяти персоны {}: ", personaId, e);
            return List.of();
        }
        return memories;
    }

    private MemoryRecord toMemory(ResultSet resultSet) throws SQLException {
        LocalDate eventDate = resultSet.getObject("event_date", LocalDate.class);
        OffsetDateTime updatedAt = resultSet.getObject("updated_at", OffsetDateTime.class);
        return new MemoryRecord(
                resultSet.getInt("id"),
                resultSet.getString("category"),
                resultSet.getString("content"),
                resultSet.getInt("importance"),
                resultSet.getBoolean("is_core"),
                eventDate,
                people(resultSet),
                resultSet.getString("source"),
                updatedAt == null ? Instant.EPOCH : updatedAt.toInstant());
    }

    private List<String> people(ResultSet resultSet) throws SQLException {
        java.sql.Array array = resultSet.getArray("people");
        if (array == null)
            return List.of();
        String[] names = (String[]) array.getArray();
        return names == null ? List.of() : List.of(names);
    }

    private void insert(Connection connection, int personaId, MemoryDraft draft,
                        Set<String> categories, Map<String, Long> users) throws SQLException {
        String query = "INSERT INTO " + PERSONA_MEMORIES_TABLE +
                " (persona_id, category, content, importance, is_core, event_date, source)" +
                " VALUES (?, ?, ?, ?, false, ?, 'auto') RETURNING id";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            statement.setString(2, category(draft.category(), categories));
            statement.setString(3, draft.content());
            statement.setInt(4, draft.importance());
            setEventDate(statement, 5, draft.eventDate());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    insertPeople(connection, resultSet.getLong(1), draft.people(), users);
            }
        }
    }

    private void update(Connection connection, int personaId, MemoryDraft draft,
                        Set<String> categories, Map<String, Long> users) throws SQLException {
        String query = "UPDATE " + PERSONA_MEMORIES_TABLE +
                " SET category = ?, content = ?, importance = ?, event_date = ?, updated_at = now()" +
                " WHERE id = ? AND persona_id = ? AND deleted_at IS NULL";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, category(draft.category(), categories));
            statement.setString(2, draft.content());
            statement.setInt(3, draft.importance());
            setEventDate(statement, 4, draft.eventDate());
            statement.setLong(5, draft.id());
            statement.setInt(6, personaId);

            if (statement.executeUpdate() == 0) {
                logger.warn("Обновление памяти {} пропущено: запись не найдена у персоны {}", draft.id(), personaId);
                return;
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + MEMORY_PEOPLE_TABLE + " WHERE memory_id = ?")) {
            statement.setLong(1, draft.id());
            statement.executeUpdate();
        }
        insertPeople(connection, draft.id(), draft.people(), users);
    }

    private void delete(Connection connection, int personaId, long memoryId) throws SQLException {
        String query = "DELETE FROM " + PERSONA_MEMORIES_TABLE + " WHERE id = ? AND persona_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, memoryId);
            statement.setInt(2, personaId);
            if (statement.executeUpdate() == 0)
                logger.warn("Удаление памяти {} пропущено: запись не найдена у персоны {}", memoryId, personaId);
        }
    }

    private void insertPeople(Connection connection, long memoryId, Collection<String> people,
                              Map<String, Long> users) throws SQLException {
        if (people == null || people.isEmpty())
            return;

        String query = "INSERT INTO " + MEMORY_PEOPLE_TABLE + " (memory_id, user_id) VALUES (?, ?)" +
                " ON CONFLICT DO NOTHING";
        Set<Long> resolved = new LinkedHashSet<>();
        for (String name : people) {
            Long userId = users.get(name.toLowerCase());
            if (userId == null) {
                logger.warn("Неизвестный человек @{}: связь с памятью {} пропущена", name, memoryId);
                continue;
            }
            resolved.add(userId);
        }

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (Long userId : resolved) {
                statement.setLong(1, memoryId);
                statement.setLong(2, userId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void saveWatermark(Connection connection, long chatId, long watermark) throws SQLException {
        String query = "INSERT INTO " + MEMORY_ANALYSIS_STATE_TABLE +
                " (chat_id, last_message_id, analyzed_at) VALUES (?, ?, now())" +
                " ON CONFLICT (chat_id) DO UPDATE" +
                " SET last_message_id = EXCLUDED.last_message_id, analyzed_at = now()";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, watermark);
            statement.executeUpdate();
        }
    }

    private String category(String requested, Set<String> categories) {
        if (requested != null && categories.contains(requested))
            return requested;

        if (requested != null && !requested.isBlank())
            logger.warn("Неизвестная категория памяти '{}': сохраняем как '{}'", requested, DEFAULT_CATEGORY);
        return DEFAULT_CATEGORY;
    }

    private void setEventDate(PreparedStatement statement, int index, LocalDate eventDate) throws SQLException {
        if (eventDate == null)
            statement.setNull(index, Types.DATE);
        else
            statement.setObject(index, eventDate);
    }

    private void putUser(Map<String, Long> users, String name, long userId) {
        if (name != null && !name.isBlank())
            users.put(name.replace("@", "").trim().toLowerCase(), userId);
    }

    // --- админка воспоминаний ---

    /** Поиск с фильтрами и постраничным выводом; порядок — важность или дата события. */
    public MemoryPage search(MemoryFilter filter) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        // Удалённые записи не показываются никому: они живут только в журнале.
        conditions.add("m.deleted_at IS NULL");
        if (filter.visiblePersonas() != null) {
            conditions.add("m.persona_id = ANY (?)");
            params.add(filter.visiblePersonas());
        }
        if (filter.personaId() != null) {
            conditions.add("m.persona_id = ?");
            params.add(filter.personaId());
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            conditions.add("m.category = ?");
            params.add(filter.category());
        }
        if (filter.personId() != null) {
            conditions.add("EXISTS (SELECT 1 FROM " + MEMORY_PEOPLE_TABLE
                    + " mp2 WHERE mp2.memory_id = m.id AND mp2.user_id = ?)");
            params.add(filter.personId());
        }
        if (filter.text() != null && !filter.text().isBlank()) {
            conditions.add("m.content ILIKE ?");
            params.add("%" + filter.text().trim() + "%");
        }
        if (filter.coreOnly()) {
            conditions.add("m.is_core");
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        int total = countMemories(where, params);

        int pageSize = Math.max(1, filter.pageSize());
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        int page = Math.min(Math.max(1, filter.page()), totalPages);

        String query = "SELECT m.id, m.category, m.content, m.importance, m.is_core, m.event_date, m.source,"
                + " m.updated_at,"
                + " array_agg(coalesce(u.user_name, u.first_name) ORDER BY coalesce(u.user_name, u.first_name))"
                + " FILTER (WHERE u.user_id IS NOT NULL) AS people"
                + " FROM " + PERSONA_MEMORIES_TABLE + " m"
                + " LEFT JOIN " + MEMORY_PEOPLE_TABLE + " mp ON mp.memory_id = m.id"
                + " LEFT JOIN " + TG_USERS_TABLE + " u ON u.user_id = mp.user_id"
                + where
                + " GROUP BY m.id ORDER BY " + order(filter.sort())
                + " LIMIT ? OFFSET ?";

        List<MemoryRecord> memories = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            int index = bind(connection, statement, params, 1);
            statement.setInt(index++, pageSize);
            statement.setInt(index, (page - 1) * pageSize);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    memories.add(toMemory(resultSet));
            }
        } catch (Exception e) {
            logger.error("Ошибка поиска воспоминаний: ", e);
            return new MemoryPage(List.of(), 0, 1, pageSize);
        }
        return new MemoryPage(memories, total, page, pageSize);
    }

    public Optional<MemoryDetails> findDetails(int memoryId) {
        String query = "SELECT id, persona_id, category, content, importance, is_core, event_date, source"
                + " FROM " + PERSONA_MEMORIES_TABLE + " WHERE id = ? AND deleted_at IS NULL";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, memoryId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new MemoryDetails(
                            resultSet.getInt("id"),
                            resultSet.getInt("persona_id"),
                            resultSet.getString("category"),
                            resultSet.getString("content"),
                            resultSet.getInt("importance"),
                            resultSet.getBoolean("is_core"),
                            resultSet.getObject("event_date", LocalDate.class),
                            resultSet.getString("source"),
                            personIds(connection, memoryId)));
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения воспоминания {}: ", memoryId, e);
        }
        return Optional.empty();
    }

    /** Ручное воспоминание: источник manual, ядро ставится сразу, если человек отметил. */
    public void createManual(int personaId, String category, String content, int importance,
                             boolean core, LocalDate eventDate, List<Long> personIds) {
        String query = "INSERT INTO " + PERSONA_MEMORIES_TABLE
                + " (persona_id, category, content, importance, is_core, event_date, source)"
                + " VALUES (?, ?, ?, ?, ?, ?, 'manual') RETURNING id";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int memoryId;
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setInt(1, personaId);
                    statement.setString(2, category);
                    statement.setString(3, content);
                    statement.setInt(4, importance);
                    statement.setBoolean(5, core);
                    setEventDate(statement, 6, eventDate);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        memoryId = resultSet.getInt(1);
                        replacePeople(connection, memoryId, personIds);
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
            logger.error("Ошибка создания воспоминания для персоны {}: ", personaId, e);
        }
    }

    /** Правка карточки: источник не меняется, люди заменяются целиком. */
    public void updateManual(int memoryId, String category, String content, int importance,
                             boolean core, LocalDate eventDate, List<Long> personIds) {
        String query = "UPDATE " + PERSONA_MEMORIES_TABLE
                + " SET category = ?, content = ?, importance = ?, is_core = ?, event_date = ?,"
                + " updated_at = now() WHERE id = ?";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, category);
                    statement.setString(2, content);
                    statement.setInt(3, importance);
                    statement.setBoolean(4, core);
                    setEventDate(statement, 5, eventDate);
                    statement.setInt(6, memoryId);
                    statement.executeUpdate();
                }
                replacePeople(connection, memoryId, personIds);

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка обновления воспоминания {}: ", memoryId, e);
        }
    }

    /** Удаление мягкое: запись остаётся в таблице и возвращается из списка «удалённые». */
    public void deleteManual(int memoryId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + PERSONA_MEMORIES_TABLE
                                + " SET deleted_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                    statement.setInt(1, memoryId);
                    statement.executeUpdate();
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка удаления воспоминания {}: ", memoryId, e);
        }
    }

    /** Возврат удалённого воспоминания: запись снова видна в списке и в промпте. */
    public void restoreManual(int memoryId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + PERSONA_MEMORIES_TABLE
                                + " SET deleted_at = NULL WHERE id = ? AND deleted_at IS NOT NULL")) {
                    statement.setInt(1, memoryId);
                    statement.executeUpdate();
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка возврата воспоминания {}: ", memoryId, e);
        }
    }

    public List<CategoryRef> categoriesWithTitles() {
        String query = "SELECT code, title FROM " + MEMORY_CATEGORIES_TABLE + " ORDER BY sort_order, code";
        List<CategoryRef> categories = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                categories.add(new CategoryRef(resultSet.getString("code"), resultSet.getString("title")));
        } catch (Exception e) {
            logger.error("Ошибка чтения справочника категорий памяти: ", e);
        }
        return categories;
    }

    public List<PersonRef> allUsers() {
        String query = "SELECT user_id, coalesce(nullif(user_name, ''), nullif(first_name, ''), user_id::text)"
                + " AS title FROM " + TG_USERS_TABLE + " ORDER BY 2";
        List<PersonRef> users = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                users.add(new PersonRef(resultSet.getLong("user_id"), resultSet.getString("title")));
        } catch (Exception e) {
            logger.error("Ошибка чтения участников чата: ", e);
        }
        return users;
    }

    private int countMemories(String where, List<Object> params) {
        String query = "SELECT count(*) FROM " + PERSONA_MEMORIES_TABLE + " m" + where;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            bind(connection, statement, params, 1);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getInt(1);
            }
        } catch (Exception e) {
            logger.error("Ошибка подсчёта воспоминаний: ", e);
        }
        return 0;
    }

    private int bind(Connection connection, PreparedStatement statement, List<Object> params, int start)
            throws SQLException {
        int index = start;
        for (Object param : params) {
            if (param instanceof Integer value)
                statement.setInt(index++, value);
            else if (param instanceof Long value)
                statement.setLong(index++, value);
            else if (param instanceof List<?> values) {
                Integer[] array = values.stream().map(value -> (Integer) value).toArray(Integer[]::new);
                statement.setArray(index++, connection.createArrayOf("integer", array));
            } else
                statement.setString(index++, String.valueOf(param));
        }
        return index;
    }

    private String order(String sort) {
        if ("date".equals(sort))
            return "m.event_date DESC NULLS LAST, m.importance DESC, m.id DESC";
        return "m.is_core DESC, m.importance DESC, m.event_date DESC NULLS LAST, m.id DESC";
    }

    private List<Long> personIds(Connection connection, int memoryId) throws SQLException {
        String query = "SELECT user_id FROM " + MEMORY_PEOPLE_TABLE + " WHERE memory_id = ? ORDER BY user_id";
        List<Long> ids = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, memoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    ids.add(resultSet.getLong(1));
            }
        }
        return ids;
    }

    private void replacePeople(Connection connection, int memoryId, List<Long> personIds) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + MEMORY_PEOPLE_TABLE + " WHERE memory_id = ?")) {
            delete.setInt(1, memoryId);
            delete.executeUpdate();
        }

        if (personIds == null || personIds.isEmpty())
            return;

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + MEMORY_PEOPLE_TABLE + " (memory_id, user_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING")) {
            for (Long userId : personIds) {
                insert.setInt(1, memoryId);
                insert.setLong(2, userId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
