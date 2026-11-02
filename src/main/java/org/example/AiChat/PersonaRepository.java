package org.example.AiChat;

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
import java.util.Optional;

import static org.example.Settings.TablesDB.CHAT_PERSONAS_TABLE;
import static org.example.Settings.TablesDB.PERSONAS_TABLE;
import static org.example.Settings.TablesDB.PERSONA_ALIASES_TABLE;

@Repository
public class PersonaRepository {
    private static final Logger logger = LoggerFactory.getLogger(PersonaRepository.class);
    private final DataSource dataSource;

    public PersonaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Persona> findForChat(long chatId) {
        String query = "SELECT p.id, p.name, p.prompt FROM " + CHAT_PERSONAS_TABLE + " cp" +
                " JOIN " + PERSONAS_TABLE + " p ON p.id = cp.persona_id WHERE cp.chat_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next())
                    return Optional.of(new Persona(
                            resultSet.getInt("id"),
                            resultSet.getString("name"),
                            resultSet.getString("prompt")));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения персоны чата {}: ", chatId, e);
        }
        return Optional.empty();
    }

    /** Алиасы персоны этого чата, от длинных к коротким. */
    public List<String> findAliasesForChat(long chatId) {
        String query = "SELECT a.alias FROM " + PERSONA_ALIASES_TABLE + " a" +
                " JOIN " + CHAT_PERSONAS_TABLE + " cp ON cp.persona_id = a.persona_id" +
                " WHERE cp.chat_id = ? ORDER BY length(a.alias) DESC";

        List<String> aliases = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    aliases.add(resultSet.getString("alias"));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения алиасов персоны чата {}: ", chatId, e);
            return Collections.emptyList();
        }
        return aliases;
    }

    /** Все чаты, у которых есть персона: единица обхода фонового анализа. */
    public List<ChatPersona> findAssignedChats() {
        String query = "SELECT chat_id, persona_id FROM " + CHAT_PERSONAS_TABLE + " ORDER BY chat_id";

        List<ChatPersona> chats = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                chats.add(new ChatPersona(resultSet.getLong("chat_id"), resultSet.getInt("persona_id")));
        } catch (Exception e) {
            logger.error("Ошибка чтения привязок персон к чатам: ", e);
            return Collections.emptyList();
        }
        return chats;
    }
}
