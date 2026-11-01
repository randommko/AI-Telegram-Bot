package org.example.Chats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.telegram.telegrambots.meta.api.objects.Chat;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.example.Settings.TablesDB.TG_CHATS_TABLE;



@Repository
public class ChatsRepository {
    private final Logger logger = LoggerFactory.getLogger(ChatsRepository.class);
    private final DataSource dataSource;

    public ChatsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertChatInDB(Chat chat) {
        Long chatID = chat.getId();
        String chatTitle = chat.getTitle();
        if (chatTitle == null)
            chatTitle = "Личный чат с: " + chat.getFirstName() + " " + chat.getLastName();
        try (Connection connection = dataSource.getConnection()) {
            String insertChatQuery = "INSERT INTO " + TG_CHATS_TABLE + " (chat_id, chat_title) VALUES (?, ?) ON CONFLICT (chat_id) DO NOTHING";
            try (PreparedStatement insertUser = connection.prepareStatement(insertChatQuery)) {
                insertUser.setLong(1, chatID);
                insertUser.setString(2, chatTitle);
                insertUser.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка при сохранении чата в БД: ", e);
        }
    }

    public Chat getChatByID(Long chatID) {
        try (Connection connection = dataSource.getConnection()) {
            String userNameQuery = "SELECT chat_id, chat_title FROM " + TG_CHATS_TABLE + " WHERE chat_id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(userNameQuery)) {
                checkStmt.setLong(1, chatID);
                ResultSet resultSet = checkStmt.executeQuery();
                if (resultSet.next())
                    return new Chat(
                            resultSet.getLong("chat_id"),
                            resultSet.getString("chat_title")
                    );
                else {
                    logger.error("ID чата не найден в БД");
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при поиске в БД чата: ", e);
            return null;
        }
    }
    
    public void updateChatInDB(Chat chat) {
        Long chatID = chat.getId();
        String chatTitle = chat.getTitle();
        if (chatTitle == null)
            chatTitle = "Личный чат с: " + chat.getFirstName() + " " + chat.getLastName();
        try (Connection connection = dataSource.getConnection()) {
            String updateChatQuery = "UPDATE " + TG_CHATS_TABLE + " SET " +
                    "chat_title = ? " +
                    "WHERE chat_id = ?"; // Условие для обновления записи по chat_id;
            //String updateChatQuery = "INSERT INTO " + TG_CHATS_TABLE + " (chat_id, chat_title) VALUES (?, ?) ON CONFLICT (chat_id) DO NOTHING";
            try (PreparedStatement insertUser = connection.prepareStatement(updateChatQuery)) {
                insertUser.setString(1, chatTitle);
                insertUser.setLong(2, chatID);
                insertUser.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка при обновлении чата в БД: ", e);
        }
    }
}
