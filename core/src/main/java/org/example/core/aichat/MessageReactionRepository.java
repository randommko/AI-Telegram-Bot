package org.example.core.aichat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.example.core.settings.Settings.USER_ROLE;
import static org.example.core.settings.TablesDB.AI_MESSAGES_TABLE;
import static org.example.core.settings.TablesDB.MESSAGE_REACTIONS_TABLE;
import static org.example.core.settings.TablesDB.READ_STATE_TABLE;

/**
 * Проход чтения: неоценённые сообщения с Telegram-идентификатором, отметка
 * прочитанного и журнал поставленных реакций.
 */
@Repository
public class MessageReactionRepository {
    private static final Logger logger = LoggerFactory.getLogger(MessageReactionRepository.class);

    /** Сообщение пачки: внутренний id для отметки и Telegram-id для реакции. */
    public record ReactionMessage(long id, int tgMessageId, String content,
                                  MessageAuthor author, Instant createdAt) {
    }

    private final DataSource dataSource;

    public MessageReactionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Отметка прочитанного; 0 означает, что проход по чату ещё не запускался. */
    public long readState(long chatId) {
        String query = "SELECT last_message_id FROM " + READ_STATE_TABLE + " WHERE chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getLong("last_message_id");
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения отметки прохода в чате {}: {}", chatId, String.valueOf(e));
        }
        return 0;
    }

    public long maxMessageId(long chatId) {
        String query = "SELECT coalesce(max(id), 0) AS last_id FROM " + AI_MESSAGES_TABLE
                + " WHERE chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getLong("last_id");
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения последнего сообщения чата {}: {}", chatId, String.valueOf(e));
        }
        return 0;
    }

    public void saveReadState(long chatId, long lastMessageId) {
        String query = "INSERT INTO " + READ_STATE_TABLE + " (chat_id, last_message_id, updated_at)"
                + " VALUES (?, ?, now()) ON CONFLICT (chat_id) DO UPDATE"
                + " SET last_message_id = EXCLUDED.last_message_id, updated_at = now()";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, lastMessageId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка записи отметки прохода в чате {}: {}", chatId, String.valueOf(e));
        }
    }

    /**
     * Новые сообщения участников после отметки, от старых к новым. Берутся последние
     * limit штук: при переполнении старые сообщения выпадают, но отметка уедет вперёд.
     */
    public List<ReactionMessage> findNewMessages(long chatId, long afterId, int limit) {
        String query = "SELECT id, tg_message_id, content, author_user_id, author_nick, author_name, created_at"
                + " FROM " + AI_MESSAGES_TABLE
                + " WHERE chat_id = ? AND id > ? AND role = ? AND tg_message_id IS NOT NULL"
                + " ORDER BY id DESC LIMIT ?";

        List<ReactionMessage> messages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, afterId);
            statement.setString(3, USER_ROLE);
            statement.setInt(4, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    messages.add(new ReactionMessage(
                            resultSet.getLong("id"),
                            Math.toIntExact(resultSet.getLong("tg_message_id")),
                            resultSet.getString("content"),
                            new MessageAuthor(
                                    resultSet.getObject("author_user_id", Long.class),
                                    resultSet.getString("author_nick"),
                                    resultSet.getString("author_name")),
                            resultSet.getTimestamp("created_at").toInstant()));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения новых сообщений чата {}: {}", chatId, String.valueOf(e));
            return List.of();
        }

        Collections.reverse(messages);
        return messages;
    }

    public int countNewMessages(long chatId, long afterId) {
        String query = "SELECT count(*) FROM " + AI_MESSAGES_TABLE
                + " WHERE chat_id = ? AND id > ? AND role = ? AND tg_message_id IS NOT NULL";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, afterId);
            statement.setString(3, USER_ROLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getInt(1);
            }
        } catch (Exception e) {
            logger.error("Ошибка подсчёта новых сообщений чата {}: {}", chatId, String.valueOf(e));
        }
        return 0;
    }

    public void saveReaction(long chatId, int tgMessageId, String emoji, String reason) {
        String query = "INSERT INTO " + MESSAGE_REACTIONS_TABLE
                + " (chat_id, tg_message_id, emoji, reason) VALUES (?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, tgMessageId);
            statement.setString(3, emoji);
            statement.setString(4, reason);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка записи реакции в журнал чата {}: {}", chatId, String.valueOf(e));
        }
    }
}
