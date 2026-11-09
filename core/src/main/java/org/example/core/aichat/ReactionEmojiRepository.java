package org.example.core.aichat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.example.core.settings.TablesDB.REACTION_EMOJIS_TABLE;

/**
 * Каталог эмодзи: набор строк задан кодом, в базе живут текст и два признака.
 * Проход чтения читает только включённые эмоциональные строки, админка — весь каталог.
 */
@Repository
public class ReactionEmojiRepository {
    private static final Logger logger = LoggerFactory.getLogger(ReactionEmojiRepository.class);

    private final DataSource dataSource;

    public ReactionEmojiRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Весь каталог для админки: строки кода, поверх них сохранённые значения. */
    public List<ReactionEmoji> findAllForAdmin() {
        Map<String, ReactionEmoji> stored = findStored();
        List<ReactionEmoji> rows = new ArrayList<>();
        for (String emoji : ReactionEmojiCatalog.ALLOWED)
            rows.add(stored.getOrDefault(emoji, new ReactionEmoji(emoji, "", false, false)));
        return rows;
    }

    /** Эмодзи, которые можно предложить модели: включённые, не метки памяти и с текстом. */
    public List<ReactionEmoji> findUsableForEmotion() {
        String query = "SELECT emoji, meaning, is_memory, active FROM " + REACTION_EMOJIS_TABLE
                + " WHERE active AND NOT is_memory AND meaning IS NOT NULL AND btrim(meaning) <> ''"
                + " ORDER BY sort_order, emoji";

        List<ReactionEmoji> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                rows.add(read(resultSet));
        } catch (Exception e) {
            logger.error("Ошибка чтения каталога эмодзи: {}", String.valueOf(e));
            return List.of();
        }
        return rows;
    }

    private Map<String, ReactionEmoji> findStored() {
        String query = "SELECT emoji, meaning, is_memory, active FROM " + REACTION_EMOJIS_TABLE;

        Map<String, ReactionEmoji> stored = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ReactionEmoji row = read(resultSet);
                stored.put(row.emoji(), row);
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения каталога эмодзи: {}", String.valueOf(e));
        }
        return stored;
    }

    /**
     * Сохраняет весь список одной транзакцией. Эмодзи вне разрешённого набора
     * отбрасываются: такой символ Telegram всё равно отклонит.
     */
    public void saveAll(List<ReactionEmoji> rows) {
        String query = "INSERT INTO " + REACTION_EMOJIS_TABLE
                + " (emoji, meaning, is_memory, active, sort_order) VALUES (?, ?, ?, ?, ?)"
                + " ON CONFLICT (emoji) DO UPDATE SET meaning = EXCLUDED.meaning,"
                + " is_memory = EXCLUDED.is_memory, active = EXCLUDED.active,"
                + " sort_order = EXCLUDED.sort_order";

        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                int order = 0;
                for (ReactionEmoji row : rows) {
                    if (!ReactionEmojiCatalog.isAllowed(row.emoji()))
                        continue;
                    order += 10;
                    statement.setString(1, row.emoji());
                    statement.setString(2, row.meaning().isBlank() ? null : row.meaning());
                    statement.setBoolean(3, row.memory());
                    statement.setBoolean(4, row.active());
                    statement.setInt(5, order);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (Exception e) {
            logger.error("Ошибка сохранения каталога эмодзи: {}", String.valueOf(e));
            throw new IllegalStateException("Каталог эмодзи не сохранён", e);
        }
    }

    private ReactionEmoji read(ResultSet resultSet) throws Exception {
        return new ReactionEmoji(
                resultSet.getString("emoji"),
                resultSet.getString("meaning"),
                resultSet.getBoolean("is_memory"),
                resultSet.getBoolean("active"));
    }
}
