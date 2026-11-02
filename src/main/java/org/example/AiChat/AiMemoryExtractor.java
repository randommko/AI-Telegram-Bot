package org.example.AiChat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;

/**
 * Фоновый анализ переписки: когда в чате накопилось достаточно новых сообщений,
 * отдельным запросом к модели извлекает факты и пополняет память персоны.
 */
@Component
public class AiMemoryExtractor {
    private static final Logger logger = LoggerFactory.getLogger(AiMemoryExtractor.class);
    private static final int DEFAULT_ANALYZE_EVERY = 50;
    private static final int DEFAULT_HISTORY_SIZE = 100;
    private static final int ANALYSIS_MAX_TOKENS = 2000;
    private static final float ANALYSIS_TEMPERATURE = 0.2f;

    private final PersonaRepository personaRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryExtractionParser parser;
    private final DeepSeekClient deepSeekClient;
    private final SettingsService settings;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean warnedAboutThreshold = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public AiMemoryExtractor(PersonaRepository personaRepository, MemoryRepository memoryRepository,
                             MemoryExtractionParser parser, DeepSeekClient deepSeekClient,
                             SettingsService settings) {
        this.personaRepository = personaRepository;
        this.memoryRepository = memoryRepository;
        this.parser = parser;
        this.deepSeekClient = deepSeekClient;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void analyzeDueChats() {
        if (started.compareAndSet(false, true))
            logger.info("Фоновый анализ памяти персон запущен: проверка каждые 5 минут");

        int threshold = readInt(AI_MEMORY_ANALYZE_EVERY, DEFAULT_ANALYZE_EVERY);
        int historySize = readInt(AI_HISTORY_SIZE, DEFAULT_HISTORY_SIZE);
        if (threshold >= historySize && warnedAboutThreshold.compareAndSet(false, true))
            logger.warn("Порог анализа {} не меньше окна истории {}: часть сообщений не попадёт в анализ",
                    threshold, historySize);

        for (ChatPersona chat : personaRepository.findAssignedChats()) {
            try {
                long watermark = memoryRepository.watermark(chat.chatId());
                int fresh = memoryRepository.countNewMessages(chat.chatId(), watermark);
                if (fresh < threshold)
                    continue;
                analyzeChat(chat, historySize);
            } catch (Exception e) {
                logger.error("Ошибка анализа памяти в чате {}: ", chat.chatId(), e);
            }
        }
    }

    void analyzeChat(ChatPersona chat, int historySize) throws Exception {
        long watermark = memoryRepository.watermark(chat.chatId());
        logWindowGap(chat.chatId(), watermark, historySize);

        List<MemoryRepository.MessageRow> messages = memoryRepository.findNewMessages(chat.chatId(), watermark);
        if (messages.isEmpty())
            return;

        List<MemoryRecord> existing = memoryRepository.findByPersona(chat.personaId());
        ArrayNode payload = buildMessages(extractPrompt(), existing, messages);

        String answer = deepSeekClient.sendRequestToAi(payload, ANALYSIS_TEMPERATURE, ANALYSIS_MAX_TOKENS);
        if (answer == null || answer.isBlank())
            throw new IllegalStateException("Модель не вернула ответ анализа памяти");

        MemoryExtraction extraction = parser.parse(answer);
        Set<Long> coreIds = MemoryCoreRules.ids(existing.stream().filter(MemoryRecord::core).toList());
        for (Long id : MemoryCoreRules.protectedDeletes(extraction.delete(), coreIds))
            logger.warn("Анализ пытался удалить ядровое воспоминание {} — операция отброшена", id);

        MemoryExtraction applied = new MemoryExtraction(
                extraction.add(),
                extraction.update(),
                MemoryCoreRules.filterDeletable(extraction.delete(), coreIds));

        long lastMessageId = messages.get(messages.size() - 1).id();
        memoryRepository.apply(
                chat.chatId(),
                chat.personaId(),
                applied,
                lastMessageId,
                memoryRepository.categories(),
                memoryRepository.userIds(peopleOf(applied)));

        logger.info("Анализ памяти в чате {}: сообщений {}, добавлено {}, обновлено {}, удалено {}",
                chat.chatId(), messages.size(), applied.add().size(), applied.update().size(), applied.delete().size());
    }

    private void logWindowGap(long chatId, long watermark, int historySize) {
        MemoryRepository.WindowInfo window = memoryRepository.window(chatId);
        if (window.count() >= historySize && window.minId() > watermark + 1)
            logger.warn("Окно истории чата {} переполнено: отметка анализа {}, самое старое сообщение {}, доступно {} —"
                            + " часть переписки выпала из анализа",
                    chatId, watermark, window.minId(), window.count());
    }

    private ArrayNode buildMessages(String prompt, List<MemoryRecord> existing,
                                    List<MemoryRepository.MessageRow> messages) {
        ArrayNode payload = objectMapper.createArrayNode();

        ObjectNode system = objectMapper.createObjectNode();
        system.put("role", "system");
        system.put("content", prompt);
        payload.add(system);

        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", "Существующие воспоминания (id | категория | текст | важность | ядро):\n"
                + existingMemories(existing)
                + "\n\nНовые сообщения:\n"
                + messages.stream()
                .map(message -> message.content())
                .collect(Collectors.joining("\n")));
        payload.add(user);

        return payload;
    }

    private String existingMemories(List<MemoryRecord> existing) {
        if (existing.isEmpty())
            return "(нет)";
        return existing.stream()
                .map(memory -> memory.id() + " | " + memory.category() + " | " + memory.content()
                        + " | " + memory.importance() + " | " + (memory.core() ? "ядро" : "обычное"))
                .collect(Collectors.joining("\n"));
    }

    private Set<String> peopleOf(MemoryExtraction extraction) {
        Set<String> people = new LinkedHashSet<>();
        extraction.add().forEach(draft -> people.addAll(draft.people()));
        extraction.update().forEach(draft -> people.addAll(draft.people()));
        return people;
    }

    private String extractPrompt() {
        String prompt = settings.getSettingValue(AI_MEMORY_EXTRACT_PROMPT);
        if (prompt == null || prompt.isBlank())
            throw new IllegalStateException("Не задан промпт анализа памяти " + AI_MEMORY_EXTRACT_PROMPT);
        return prompt;
    }

    private int readInt(String key, int defaultValue) {
        String configured = settings.getSettingValue(key);
        if (configured == null)
            return defaultValue;

        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed > 0)
                return parsed;
            logger.warn("Некорректное значение настройки {} '{}': используется {}", key, configured, defaultValue);
        } catch (NumberFormatException e) {
            logger.warn("Некорректное значение настройки {} '{}': используется {}", key, configured, defaultValue);
        }
        return defaultValue;
    }
}
