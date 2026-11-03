package org.example.Admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.example.Settings.TablesDB.ADMIN_ACCOUNTS_TABLE;
import static org.example.Settings.TablesDB.CHAT_MODERATORS_TABLE;
import static org.example.Settings.TablesDB.TG_USERS_TABLE;

/** Учётные записи модераторов и поиск человека в tg_users по нику или id. */
@Repository
public class AdminAccountsRepository {
    private static final Logger logger = LoggerFactory.getLogger(AdminAccountsRepository.class);
    private final DataSource dataSource;
    private final ChangeJournal journal;

    public AdminAccountsRepository(DataSource dataSource, ChangeJournal journal) {
        this.dataSource = dataSource;
        this.journal = journal;
    }

    /** Данные для проверки пароля: без ника и имени, они нужны только странице. */
    public record AdminCredentials(int id, long tgUserId, String login, String passwordHash) {
    }

    /** Человек из tg_users: кандидат в модераторы. */
    public record AdminPerson(long tgUserId, String nick, String name) {
    }

    /** Строка раздела «Модераторы». */
    public record AdminAccount(int id, long tgUserId, String login, String nick, String name,
                               String chats, List<Long> chatIds,
                               OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
    }

    public Optional<AdminCredentials> findByLogin(String login) {
        String query = "SELECT id, tg_user_id, login, password_hash FROM " + ADMIN_ACCOUNTS_TABLE
                + " WHERE lower(login) = lower(?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, login);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(new AdminCredentials(
                            resultSet.getInt("id"),
                            resultSet.getLong("tg_user_id"),
                            resultSet.getString("login"),
                            resultSet.getString("password_hash")));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения учётки модератора {}: ", login, e);
        }
        return Optional.empty();
    }

    public List<AdminAccount> findAll() {
        String query = "SELECT a.id, a.tg_user_id, a.login, a.created_at, a.last_login_at,"
                + " u.user_name, u.first_name,"
                + " (SELECT array_agg(cm.chat_id ORDER BY cm.chat_id) FROM " + CHAT_MODERATORS_TABLE + " cm"
                + " WHERE cm.tg_user_id = a.tg_user_id) AS chat_ids"
                + " FROM " + ADMIN_ACCOUNTS_TABLE + " a"
                + " JOIN " + TG_USERS_TABLE + " u ON u.user_id = a.tg_user_id"
                + " ORDER BY lower(a.login)";

        List<AdminAccount> accounts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                List<Long> chatIds = chatIds(resultSet);
                accounts.add(new AdminAccount(
                        resultSet.getInt("id"),
                        resultSet.getLong("tg_user_id"),
                        resultSet.getString("login"),
                        resultSet.getString("user_name"),
                        resultSet.getString("first_name"),
                        chatIds.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse(""),
                        chatIds,
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("last_login_at", OffsetDateTime.class)));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения списка модераторов: ", e);
        }
        return accounts;
    }

    /** Человек по {@code @нику} или числовому id; ник в tg_users хранится с ведущей «@». */
    public Optional<AdminPerson> findPerson(String input) {
        if (input == null || input.isBlank())
            return Optional.empty();

        String value = input.trim();
        while (value.startsWith("@"))
            value = value.substring(1).trim();
        if (value.isEmpty())
            return Optional.empty();

        boolean byId = value.matches("\\d+");
        String query = "SELECT user_id, user_name, first_name FROM " + TG_USERS_TABLE
                + (byId ? " WHERE user_id = ?"
                : " WHERE lower(replace(coalesce(user_name, ''), '@', '')) = lower(?)");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            if (byId)
                statement.setLong(1, Long.parseLong(value));
            else
                statement.setString(1, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(new AdminPerson(
                            resultSet.getLong("user_id"),
                            resultSet.getString("user_name"),
                            resultSet.getString("first_name")));
            }
        } catch (Exception e) {
            logger.error("Ошибка поиска человека {}: ", input, e);
        }
        return Optional.empty();
    }

    /** Telegram id владельца учётки: по нему выдаются чаты. */
    public Optional<Long> tgUserIdOf(int accountId) {
        String query = "SELECT tg_user_id FROM " + ADMIN_ACCOUNTS_TABLE + " WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(resultSet.getLong("tg_user_id"));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения учётки {}: ", accountId, e);
        }
        return Optional.empty();
    }

    public boolean loginTaken(String login) {
        String query = "SELECT 1 FROM " + ADMIN_ACCOUNTS_TABLE + " WHERE lower(login) = lower(?) LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, login);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            logger.error("Ошибка проверки логина {}: ", login, e);
        }
        return false;
    }

    public boolean personTaken(long tgUserId) {
        String query = "SELECT 1 FROM " + ADMIN_ACCOUNTS_TABLE + " WHERE tg_user_id = ? LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, tgUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            logger.error("Ошибка проверки человека {}: ", tgUserId, e);
        }
        return false;
    }

    /** Новая учётка и запись в журнале — одной транзакцией. */
    public void create(long tgUserId, String login, String passwordHash, Actor actor) {
        String query = "INSERT INTO " + ADMIN_ACCOUNTS_TABLE
                + " (tg_user_id, login, password_hash) VALUES (?, ?, ?) RETURNING id";

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setLong(1, tgUserId);
                    statement.setString(2, login);
                    statement.setString(3, passwordHash);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        id = resultSet.getInt(1);
                    }
                }

                journal.record(connection, actor, ChangeJournal.ACCOUNT, id, null, null, Map.of(),
                        Map.of("login", login, "tg_user_id", tgUserId));
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка создания учётки модератора {}: ", login, e);
        }
    }

    public void delete(int id, Actor actor) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String login = null;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT login FROM " + ADMIN_ACCOUNTS_TABLE + " WHERE id = ?")) {
                    statement.setInt(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next())
                            login = resultSet.getString("login");
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + ADMIN_ACCOUNTS_TABLE + " WHERE id = ?")) {
                    statement.setInt(1, id);
                    statement.executeUpdate();
                }

                if (login != null)
                    journal.record(connection, actor, ChangeJournal.ACCOUNT, id, null, null,
                            Map.of("login", login), Map.of());
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка удаления учётки {}: ", id, e);
        }
    }

    /** Хеш пароля в журнал не попадает: хранится только факт смены. */
    public void resetPassword(int id, String passwordHash, Actor actor) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE " + ADMIN_ACCOUNTS_TABLE + " SET password_hash = ? WHERE id = ?")) {
                    statement.setString(1, passwordHash);
                    statement.setInt(2, id);
                    statement.executeUpdate();
                }

                journal.record(connection, actor, ChangeJournal.ACCOUNT, id, null, null, Map.of(),
                        Map.of("password", "изменён"));
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка смены пароля учётки {}: ", id, e);
        }
    }

    public void markLogin(String login) {
        String query = "UPDATE " + ADMIN_ACCOUNTS_TABLE + " SET last_login_at = now()"
                + " WHERE lower(login) = lower(?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, login);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка отметки входа {}: ", login, e);
        }
    }

    private List<Long> chatIds(ResultSet resultSet) throws Exception {
        Array array = resultSet.getArray("chat_ids");
        if (array == null)
            return List.of();

        Long[] ids = (Long[]) array.getArray();
        return ids == null ? List.of() : List.of(ids);
    }
}
