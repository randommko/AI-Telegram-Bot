package org.example.Admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.example.Settings.TablesDB.CHAT_MODERATORS_TABLE;

/** Выданные модератору чаты: единственный источник его прав. */
@Repository
public class ChatModeratorRepository {
    private static final Logger logger = LoggerFactory.getLogger(ChatModeratorRepository.class);
    private final DataSource dataSource;

    public ChatModeratorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Long> chatIdsOf(long tgUserId) {
        String query = "SELECT chat_id FROM " + CHAT_MODERATORS_TABLE + " WHERE tg_user_id = ? ORDER BY chat_id";

        List<Long> chats = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    chats.add(resultSet.getLong("chat_id"));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения чатов модератора {}: ", tgUserId, e);
        }
        return chats;
    }

    public boolean isModeratorOf(long tgUserId, long chatId) {
        String query = "SELECT 1 FROM " + CHAT_MODERATORS_TABLE
                + " WHERE tg_user_id = ? AND chat_id = ? LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            statement.setLong(2, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            logger.error("Ошибка проверки доступа модератора {} к чату {}: ", tgUserId, chatId, e);
        }
        return false;
    }

    public void grant(long tgUserId, long chatId, Long grantedBy) {
        String query = "INSERT INTO " + CHAT_MODERATORS_TABLE + " (chat_id, tg_user_id, granted_by)"
                + " VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            statement.setLong(2, tgUserId);
            statement.setObject(3, grantedBy, java.sql.Types.BIGINT);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка выдачи чата {} модератору {}: ", chatId, tgUserId, e);
        }
    }

    public void revoke(long tgUserId, long chatId) {
        String query = "DELETE FROM " + CHAT_MODERATORS_TABLE + " WHERE tg_user_id = ? AND chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            statement.setLong(2, chatId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка отзыва чата {} у модератора {}: ", chatId, tgUserId, e);
        }
    }
}
