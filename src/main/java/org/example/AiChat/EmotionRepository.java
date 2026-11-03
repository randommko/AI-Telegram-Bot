package org.example.AiChat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.Settings.TablesDB.EMOTION_PHRASES_TABLE;
import static org.example.Settings.TablesDB.EMOTIONS_TABLE;
import static org.example.Settings.TablesDB.PERSONA_EMOTIONS_TABLE;

/** Справочник эмоций, фразы по полосам и значения эмоций у персон. */
@Repository
public class EmotionRepository {
    private static final Logger logger = LoggerFactory.getLogger(EmotionRepository.class);

    private final DataSource dataSource;

    public EmotionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Все шкалы справочника в порядке вывода, включая производную агрессию. */
    public List<Emotion> findCatalog() {
        String query = "SELECT code, title, description, polarity, aggression_weight, decay_per_hour,"
                + " base_default, is_derived, sort_order FROM " + EMOTIONS_TABLE
                + " ORDER BY sort_order, code";

        List<Emotion> emotions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next())
                emotions.add(new Emotion(
                        resultSet.getString("code"),
                        resultSet.getString("title"),
                        resultSet.getString("description"),
                        resultSet.getString("polarity"),
                        resultSet.getDouble("aggression_weight"),
                        resultSet.getDouble("decay_per_hour"),
                        resultSet.getInt("base_default"),
                        resultSet.getBoolean("is_derived"),
                        resultSet.getInt("sort_order")));
        } catch (Exception e) {
            logger.error("Ошибка чтения справочника эмоций: ", e);
            return List.of();
        }
        return emotions;
    }

    /** Фразы шкал, сгруппированные по эмоции и отсортированные по полосе. */
    public Map<String, List<EmotionPhrase>> findPhrases() {
        String query = "SELECT emotion_code, from_value, to_value, phrase FROM " + EMOTION_PHRASES_TABLE
                + " ORDER BY emotion_code, from_value";

        Map<String, List<EmotionPhrase>> phrases = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                EmotionPhrase phrase = new EmotionPhrase(
                        resultSet.getString("emotion_code"),
                        resultSet.getInt("from_value"),
                        resultSet.getInt("to_value"),
                        resultSet.getString("phrase"));
                phrases.computeIfAbsent(phrase.emotionCode(), code -> new ArrayList<>()).add(phrase);
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения фраз эмоций: ", e);
            return Map.of();
        }
        return phrases;
    }

    /**
     * Значения эмоций персоны. Перед чтением добиваем недостающие строки
     * умолчаниями справочника: персона могла быть создана до появления эмоций
     * или руками в базе.
     */
    public List<PersonaEmotion> findForPersona(int personaId) {
        ensureForPersona(personaId);
        return selectForPersona(personaId);
    }

    /** Значения без добивки недостающих строк: предпросмотр промпта не должен писать в базу. */
    public List<PersonaEmotion> findStoredForPersona(int personaId) {
        return selectForPersona(personaId);
    }

    private void ensureForPersona(int personaId) {
        String query = "INSERT INTO " + PERSONA_EMOTIONS_TABLE
                + " (persona_id, emotion_code, base_value, value, updated_at)"
                + " SELECT ?, e.code, e.base_default, e.base_default, now() FROM " + EMOTIONS_TABLE + " e"
                + " WHERE e.is_derived = false"
                + " ON CONFLICT (persona_id, emotion_code) DO NOTHING";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка создания значений эмоций персоны {}: ", personaId, e);
        }
    }

    private List<PersonaEmotion> selectForPersona(int personaId) {
        String query = "SELECT emotion_code, base_value, value, updated_at FROM " + PERSONA_EMOTIONS_TABLE
                + " WHERE persona_id = ? ORDER BY emotion_code";

        List<PersonaEmotion> emotions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next())
                    emotions.add(new PersonaEmotion(
                            personaId,
                            resultSet.getString("emotion_code"),
                            resultSet.getInt("base_value"),
                            resultSet.getInt("value"),
                            resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()));
            }
        } catch (Exception e) {
            logger.error("Ошибка чтения эмоций персоны {}: ", personaId, e);
            return List.of();
        }
        return emotions;
    }

    /**
     * Базовые значения эмоций персоны. Если базу подняли выше текущего значения,
     * текущее поднимается вместе с ней сразу: выставленный в админке характер
     * должен действовать со следующего ответа, а не через несколько часов.
     * При понижении базы настроение остаётся и затухает вниз само.
     */
    public void saveBaseValues(Connection connection, int personaId, Map<String, Integer> baseValues) throws Exception {
        if (baseValues == null || baseValues.isEmpty())
            return;

        String query = "INSERT INTO " + PERSONA_EMOTIONS_TABLE
                + " (persona_id, emotion_code, base_value, value, updated_at)"
                + " VALUES (?, ?, ?, ?, now())"
                + " ON CONFLICT (persona_id, emotion_code) DO UPDATE SET"
                + " base_value = EXCLUDED.base_value,"
                + " value = GREATEST(" + PERSONA_EMOTIONS_TABLE + ".value, EXCLUDED.base_value),"
                + " updated_at = CASE WHEN EXCLUDED.base_value > " + PERSONA_EMOTIONS_TABLE + ".value"
                + " THEN now() ELSE " + PERSONA_EMOTIONS_TABLE + ".updated_at END";

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (Map.Entry<String, Integer> entry : baseValues.entrySet()) {
                statement.setInt(1, personaId);
                statement.setString(2, entry.getKey());
                statement.setInt(3, entry.getValue());
                statement.setInt(4, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Коды редактируемых эмоций: производные шкалы значения у персоны не имеют. */
    public Set<String> editableCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (Emotion emotion : findCatalog()) {
            if (!emotion.derived())
                codes.add(emotion.code());
        }
        return codes;
    }

    /** Правка справочника: производная агрессия не редактируется, её значения вычисляются. */
    public void updateEmotion(String code, String title, double aggressionWeight, double decayPerHour,
                              int baseDefault) {
        String query = "UPDATE " + EMOTIONS_TABLE + " SET title = ?, aggression_weight = ?, decay_per_hour = ?,"
                + " base_default = ? WHERE code = ? AND is_derived = false";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, title);
            statement.setDouble(2, aggressionWeight);
            statement.setDouble(3, decayPerHour);
            statement.setInt(4, baseDefault);
            statement.setString(5, code);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка правки эмоции {}: ", code, e);
        }
    }

    /** Правка фразы полосы: строка полосы уже есть в справочнике, меняется только текст. */
    public void updatePhrase(String emotionCode, int fromValue, String phrase) {
        String query = "UPDATE " + EMOTION_PHRASES_TABLE + " SET phrase = ?"
                + " WHERE emotion_code = ? AND from_value = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, phrase);
            statement.setString(2, emotionCode);
            statement.setInt(3, fromValue);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка правки фразы эмоции {} полосы {}: ", emotionCode, fromValue, e);
        }
    }

    /**
     * Применяет сдвиги к действующим значениям эмоций одной транзакцией: строки
     * персоны блокируются, чтобы два одновременных ответа не потеряли прибавку.
     */
    public Map<String, Integer> applyShifts(int personaId, Map<String, Integer> deltas, int maxDelta) {
        if (deltas == null || deltas.isEmpty())
            return Map.of();

        String select = "SELECT pe.emotion_code, pe.base_value, pe.value, pe.updated_at, e.decay_per_hour,"
                + " e.is_derived FROM " + PERSONA_EMOTIONS_TABLE + " pe"
                + " JOIN " + EMOTIONS_TABLE + " e ON e.code = pe.emotion_code"
                + " WHERE pe.persona_id = ? FOR UPDATE OF pe";
        String update = "UPDATE " + PERSONA_EMOTIONS_TABLE
                + " SET value = ?, updated_at = now() WHERE persona_id = ? AND emotion_code = ?";

        Map<String, Integer> applied = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = Instant.now();
                Map<String, Integer> effective = new LinkedHashMap<>();
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setInt(1, personaId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            if (resultSet.getBoolean("is_derived"))
                                continue;
                            effective.put(resultSet.getString("emotion_code"), EmotionService.effectiveValue(
                                    resultSet.getInt("base_value"),
                                    resultSet.getInt("value"),
                                    resultSet.getDouble("decay_per_hour"),
                                    Duration.between(resultSet.getObject("updated_at", OffsetDateTime.class)
                                            .toInstant(), now)));
                        }
                    }
                }

                applied.putAll(EmotionService.shiftAll(effective, deltas, maxDelta));
                try (PreparedStatement updateStatement = connection.prepareStatement(update)) {
                    for (Map.Entry<String, Integer> entry : applied.entrySet()) {
                        updateStatement.setInt(1, entry.getValue());
                        updateStatement.setInt(2, personaId);
                        updateStatement.setString(3, entry.getKey());
                        updateStatement.addBatch();
                    }
                    updateStatement.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.error("Ошибка применения сдвигов эмоций персоны {}: ", personaId, e);
            return Map.of();
        }
        return applied;
    }

    /** Гнев после срыва возвращается к характеру; остальные шкалы не трогаются. */
    public void resetToBase(int personaId, String emotionCode) {
        String query = "UPDATE " + PERSONA_EMOTIONS_TABLE
                + " SET value = base_value, updated_at = now() WHERE persona_id = ? AND emotion_code = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, personaId);
            statement.setString(2, emotionCode);
            statement.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка сброса эмоции {} персоны {}: ", emotionCode, personaId, e);
        }
    }
}
