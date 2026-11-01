package org.example.CockSize;

import org.example.DTO.AVGCockSizeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.example.Settings.TablesDB.*;

@Repository
public class CockSizeRepository {
    private final Logger logger = LoggerFactory.getLogger(CockSizeRepository.class);
    private final DataSource dataSource;

    public CockSizeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setCockSizeWinner (Long userID, Integer size) {
        LocalDate today = LocalDate.now();
        String insertQuery = "INSERT INTO " + COCKSIZE_STATS_TABLE + " (user_id, size, date) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                insertStmt.setLong(1, userID);
                insertStmt.setInt(2, size);
                insertStmt.setDate(3, Date.valueOf(today));
                insertStmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка при записи в БД длинны члена: ", e);
        }
    }

    public String getCockSizeImageName(Integer size) {
        String selectQuery = "SELECT img FROM " + COCKSIZE_IMAGES_TABLE + " WHERE cock_size = ?";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insertStmt = connection.prepareStatement(selectQuery)) {
                insertStmt.setInt(1, size);
                ResultSet resultImageSet = insertStmt.executeQuery();
                if (resultImageSet.next())
                    return resultImageSet.getString("img");
            }
        } catch (Exception e) {
            logger.error("Ошибка при записи в БД длинны члена: ", e);
        }
        return null;
    }

    public int getTodayPlayerCockSize(Long userID) {
        LocalDate currentDate = LocalDate.now();
        try (Connection connection = dataSource.getConnection()) {
            // Проверяем, есть ли запись для текущей даты
            String checkQuery = "SELECT size FROM " + COCKSIZE_STATS_TABLE + " WHERE user_id = ? AND date = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                checkStmt.setLong(1, userID);
                checkStmt.setDate(2, Date.valueOf(currentDate));
                ResultSet resultSet = checkStmt.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getInt("size");
                } else {
                    logger.info("Для user_id: {} не найден замер члена на текущую дату, date: {}", userID, currentDate);
                    return -1; // Возвращаем значение по умолчанию, если запись не найдена
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при поиске в БД длинны члена: ", e);
            return -1;
        }
    }
    public List<Integer> getPlayerCockSizeByDays(Long userID, Integer days) {
        try (Connection connection = dataSource.getConnection()) {
            LocalDate dateTwoDaysAgo = LocalDate.now().minusDays(2);
            String checkQuery = "SELECT size FROM " + COCKSIZE_STATS_TABLE + " WHERE user_id = ? AND date >= ? LIMIT ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                checkStmt.setLong(1, userID);
                checkStmt.setDate(2, Date.valueOf(dateTwoDaysAgo));
                checkStmt.setInt(3, days);
                ResultSet resultSet = checkStmt.executeQuery();
                List<Integer> lastSizes = new ArrayList<>();
                while (resultSet.next()) {
                    lastSizes.add(resultSet.getInt("size"));
                }
                return lastSizes;
            }
        } catch (Exception e) {
            logger.error("Ошибка при поиске в БД длинны члена за последние {} дней: ", days, e);
            return null;
        }
    }

    public AVGCockSizeDTO getAVGCockSize(Long userID) {
        try (Connection connection = dataSource.getConnection()) {
            String checkQuery = "SELECT " +
                    "    AVG(size) AS average_size, " +
                    "    COUNT(size) AS measurement_count, " +
                    "    MIN(date) AS first_measurement_date, " +
                    "    MAX(date) AS last_measurement_date " +
                    "FROM " + COCKSIZE_STATS_TABLE + " WHERE user_id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
                checkStmt.setLong(1, userID);
                ResultSet resultSet = checkStmt.executeQuery();
                if (resultSet.next()) {
                    return new AVGCockSizeDTO(
                            userID,
                            resultSet.getFloat("average_size"),
                            resultSet.getDate("first_measurement_date").toLocalDate(),
                            resultSet.getDate("last_measurement_date").toLocalDate(),
                            resultSet.getInt("measurement_count")
                    );
                } else {
                    logger.info("Для user_id: {} не найдено ни одного замер члена", userID);
                    return null; // Возвращаем значение по умолчанию, если запись не найдена
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при поиске в БД средней длинны члена: ", e);
            return null;
        }
    }
}
