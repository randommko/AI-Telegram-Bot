package org.example.core.aichat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.example.core.settings.Settings.USER_ROLE;
import static org.example.core.settings.TablesDB.AI_CHAT_STATE_TABLE;
import static org.example.core.settings.TablesDB.AI_MESSAGES_TABLE;

@Repository
public class AiChatRepository {
    private static final Logger logger = LoggerFactory.getLogger(AiChatRepository.class);
    private final DataSource dataSource;

    public AiChatRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveMessage(Long chatId, String role, String content, int limit) {
        saveMessage(chatId, role, content, limit, MessageAuthor.none(), null);
    }

    public void saveMessage(Long chatId, String role, String content, int limit, MessageAuthor author) {
        saveMessage(chatId, role, content, limit, author, null);
    }

    /**
     * @param tgMessageId идентификатор сообщения в Telegram; нужен проходу чтения,
     *                    чтобы поставить реакцию. У ответов бота и служебных
     *                    сообщений его нет — тогда передаётся null.
     */
    public void saveMessage(Long chatId, String role, String content, int limit,
                            MessageAuthor author, Integer tgMessageId) {
        String insertQuery = "INSERT INTO " + AI_MESSAGES_TABLE
                + " (chat_id, role, content, tg_message_id, author_user_id, author_nick, author_name)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                // Повторный апдейт того же сообщения не должен создавать дубль.
                + " ON CONFLICT (chat_id, tg_message_id) WHERE tg_message_id IS NOT NULL DO NOTHING";
        // Окно истории на чат: всё, что вышло за последние limit сообщений, удаляется сразу.
        String trimQuery = "DELETE FROM " + AI_MESSAGES_TABLE +
                " WHERE chat_id = ? AND id NOT IN (SELECT id FROM " + AI_MESSAGES_TABLE +
                " WHERE chat_id = ? ORDER BY id DESC LIMIT ?)";

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(insertQuery)) {
                insert.setLong(1, chatId);
                insert.setString(2, role);
                insert.setString(3, content);
                if (tgMessageId == null)
                    insert.setNull(4, java.sql.Types.BIGINT);
                else
                    insert.setLong(4, tgMessageId);
                setAuthor(insert, 5, author);
                insert.executeUpdate();
            }

            try (PreparedStatement trim = connection.prepareStatement(trimQuery)) {
                trim.setLong(1, chatId);
                trim.setLong(2, chatId);
                trim.setInt(3, limit);
                trim.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка сохранения сообщения AI в чате {}: ", chatId, e);
        }
    }

    /** Участник чата: сколько сообщений написал и когда последний раз. */
    public record AuthorStat(long userId, String nick, String name, int messages, long lastMessageId) {
    }

    /**
     * До limit самых свежих авторов чата со счётчиком сообщений. Окно — вся история,
     * а не последние 50 сообщений: иначе замолчавший участник исчезает из промпта.
     */
    public List<AuthorStat> findAuthorStats(Long chatId, int limit) {
        String query = "SELECT author_user_id, max(author_nick) AS nick, max(author_name) AS name,"
                + " count(*) AS messages, max(id) AS last_id"
                + " FROM " + AI_MESSAGES_TABLE
                + " WHERE chat_id = ? AND role = ? AND author_user_id IS NOT NULL"
                + " GROUP BY author_user_id ORDER BY max(id) DESC LIMIT ?";

        List<AuthorStat> authors = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setString(2, USER_ROLE);
            statement.setInt(3, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    authors.add(new AuthorStat(
                            resultSet.getLong("author_user_id"),
                            resultSet.getString("nick"),
                            resultSet.getString("name"),
                            resultSet.getInt("messages"),
                            resultSet.getLong("last_id")));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения участников чата {}: ", chatId, e);
            return List.of();
        }
        return authors;
    }

    public List<AiMessage> findLastMessages(Long chatId, int limit) {
        String query = "SELECT role, content, author_user_id, author_nick, author_name FROM " + AI_MESSAGES_TABLE +
                " WHERE chat_id = ? ORDER BY id DESC LIMIT ?";
        List<AiMessage> messages = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    messages.add(new AiMessage(
                            resultSet.getString("role"),
                            resultSet.getString("content"),
                            new MessageAuthor(
                                    resultSet.getObject("author_user_id", Long.class),
                                    resultSet.getString("author_nick"),
                                    resultSet.getString("author_name"))));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения истории AI в чате {}: ", chatId, e);
            return Collections.emptyList();
        }

        // Читали от новых к старым — в запрос к AI история уходит в хронологическом порядке.
        Collections.reverse(messages);
        return messages;
    }

    public int countMessages(Long chatId) {
        String query = "SELECT count(*) FROM " + AI_MESSAGES_TABLE + " WHERE chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getInt(1);
            }
        } catch (Exception e) {
            logger.error("Ошибка подсчёта истории AI в чате {}: ", chatId, e);
        }
        return 0;
    }

    public void clearMessages(Long chatId) {
        String query = "DELETE FROM " + AI_MESSAGES_TABLE + " WHERE chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка очистки истории AI в чате {}: ", chatId, e);
        }
    }

    public int incrementResets(Long chatId) {
        String query = "INSERT INTO " + AI_CHAT_STATE_TABLE + " (chat_id, resets) VALUES (?, 1)" +
                " ON CONFLICT (chat_id) DO UPDATE SET resets = " + AI_CHAT_STATE_TABLE + ".resets + 1" +
                " RETURNING resets";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return resultSet.getInt("resets");
            }
        } catch (Exception e) {
            logger.error("Ошибка счётчика сбросов AI в чате {}: ", chatId, e);
        }
        return 0;
    }

    /** Автор пишется тремя полями: ID для связей, ник и имя для подписи в промпте. */
    private void setAuthor(PreparedStatement statement, int firstIndex, MessageAuthor author) throws Exception {
        MessageAuthor value = author == null ? MessageAuthor.none() : author;
        if (value.userId() == null)
            statement.setNull(firstIndex, java.sql.Types.BIGINT);
        else
            statement.setLong(firstIndex, value.userId());
        statement.setString(firstIndex + 1, value.nick());
        statement.setString(firstIndex + 2, value.name());
    }
}
