package org.example.PidorGame;

import org.example.Users.UsersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.example.Settings.TablesDB.*;

@Repository
public class PidorGameRepository {
    private static final Logger logger = LoggerFactory.getLogger(PidorGameRepository.class);
    private final UsersService usersService;
    private final DataSource dataSource;

    public PidorGameRepository(DataSource dataSource, UsersService usersService) {
        this.dataSource = dataSource;
        this.usersService = usersService;
    }

    public void setPidorWinner(Long chatID, Long userID) {
        LocalDate today = LocalDate.now();
        String insertQuery = "INSERT INTO " + PIDOR_STATS_TABLE + " (chat_id, date, user_id) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                insertStmt.setLong(1, chatID);
                insertStmt.setDate(2, Date.valueOf(today));
                insertStmt.setLong(3, userID);
                insertStmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка при приверке состоявшейся игры: ", e);
        }
    }

    public Set<Long> getPidorGamePlayers(Long chatID) {
        Set<Long> chatPlayers = new HashSet<>();
        try (Connection getPlayersConn = dataSource.getConnection()) {
            String query = "SELECT user_id FROM " + PIDOR_PLAYERS_TABLE + " WHERE chat_id = ?";
            PreparedStatement stmt = getPlayersConn.prepareStatement(query);
            stmt.setLong(1, chatID);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next())
                    chatPlayers.add(rs.getLong("user_id"));
            }
        } catch (Exception e) {
            logger.error("Ошибка получения списка игроков из БД: ", e);
        }
        return chatPlayers;
    }
    public Integer registerPlayer(Long chatID, Long userID) {
        String insertQuery = "INSERT INTO " + PIDOR_PLAYERS_TABLE + " (chat_id, user_id) VALUES (?, ?) ON CONFLICT (chat_id, user_id) DO NOTHING";

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);

            preparedStatement.setLong(1, chatID);
            preparedStatement.setLong(2, userID);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Ошибка при регистрации игрока в БД: {}", String.valueOf(e));
            return 0;
        }
    }

    public Map<String, Integer> getPidorStats(Long chatID) {
        Map<String, Integer> winnersList = new HashMap<>();
        String query = "SELECT pst.user_id, COUNT(*) AS count " +
                "FROM " + PIDOR_STATS_TABLE +
                " AS pst JOIN " + TG_USERS_TABLE +
                " AS tut ON pst.user_id = tut.user_id " +
                "WHERE pst.chat_id = ? " +
                "GROUP BY pst.user_id";

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setLong(1, chatID);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next())
                winnersList.put(
                        usersService.getUserNameByID(resultSet.getLong("user_id")),
                        resultSet.getInt("count")
                );
        } catch (Exception e) {
            logger.error("Ошибка получения статистики пидоров: {}", String.valueOf(e));
        }
        return winnersList;
    }

    public Long getTodayWinner(Long chatID) {
        try (Connection connection = dataSource.getConnection()) {
            LocalDate today = LocalDate.now();

            String checkQuery = "SELECT user_id FROM " + PIDOR_STATS_TABLE + " WHERE chat_id = ? AND date = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                checkStmt.setLong(1, chatID);
                checkStmt.setDate(2, Date.valueOf(today));
                try (ResultSet resultSet = checkStmt.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong("user_id");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при проверке состоявшейся игры: ", e);
        }
        return null;
    }

    public String getWinnerResponse() {
        String sql = "SELECT text FROM " + MESSAGES_TABLE + " WHERE group_num = 100 ORDER BY RANDOM() LIMIT 1";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("text");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Произошла ошибка при получении текста сообщений из БД: ", e);
            // В случае ошибки возвращаем предопределённые ответы
            return "Победитель сегодняшней игры: ";
        }
        return "Победитель сегодняшней игры: ";
    }

    public List<String> getRandomResponses() {
        // SQL запрос для выборки случайного текста из группы
        String sql = "SELECT text FROM " + MESSAGES_TABLE + " WHERE group_num = ? ORDER BY RANDOM() LIMIT 1";

        List<String> responses = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            for (int groupNum = 1; groupNum <= 3; groupNum++) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, groupNum);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            responses.add(rs.getString("text"));
                        } else {
                            // Если в группе нет записей, добавляем запасной текст
                            responses.add("Запасной текст для группы " + groupNum);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Произошла ошибка при получении текста сообщений из БД: ", e);
            // В случае ошибки возвращаем предопределённые ответы
            return List.of("Подготовка...", "Скоро узнаем...", "Держитесь крепче...");
        }

        return responses;
    }
}
