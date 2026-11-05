package org.example.bot.aichat;

import org.example.core.aichat.AuthorFormat;
import org.example.core.aichat.ChatPersona;
import org.example.core.aichat.MemoryCoreRules;
import org.example.core.aichat.MemoryExtraction;
import org.example.core.aichat.MemoryRecord;
import org.example.core.aichat.MemoryRepository;
import org.example.core.aichat.Persona;
import org.example.core.aichat.PersonaRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.example.core.settings.Settings.AI_HISTORY_SIZE;
import static org.example.core.settings.Settings.AI_MEMORY_ANALYSIS_MAX_TOKENS;
import static org.example.core.settings.Settings.AI_MEMORY_ANALYSIS_MESSAGES;
import static org.example.core.settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.core.settings.Settings.AI_MEMORY_EXTRACT_PROMPT;

/**
 * Фоновый анализ переписки: когда в чате накопилось достаточно новых сообщений,
 * отдельным запросом к модели извлекает факты и пополняет память персоны.
 */
@Component
public class AiMemoryExtractor {
    private static final Logger logger = LoggerFactory.getLogger(AiMemoryExtractor.class);
    private static final int DEFAULT_ANALYZE_EVERY = 50;
    private static final int DEFAULT_HISTORY_SIZE = 100;
    private static final int DEFAULT_ANALYSIS_MESSAGES = 50;
    private static final int DEFAULT_ANALYSIS_MAX_TOKENS = 8000;
    private static final int MAX_ANALYSIS_MESSAGES = 1000;
    private static final int MAX_ANALYSIS_TOKENS = 8192;
    private static final float ANALYSIS_TEMPERATURE = 0.2f;
    private static final DateTimeFormatter MESSAGE_TIME =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter MEMORY_UPDATED =
            DateTimeFormatter.ofPattern("dd.MM").withZone(ZoneId.systemDefault());

    /** Итог прогона: сколько разобрано и что сделано с памятью. */
    public record AnalysisResult(int messages, int added, int updated, int deleted, int rejectedDeletes) {
    }

    private final PersonaRepository personaRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryExtractionParser parser;
    private final LlmClient llmClient;
    private final SettingsService settings;
    private final MemoryUpdateNotifier notifier;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean warnedAboutThreshold = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public AiMemoryExtractor(PersonaRepository personaRepository, MemoryRepository memoryRepository,
                             MemoryExtractionParser parser, LlmClient llmClient,
                             SettingsService settings, MemoryUpdateNotifier notifier) {
        this.personaRepository = personaRepository;
        this.memoryRepository = memoryRepository;
        this.parser = parser;
        this.llmClient = llmClient;
        this.settings = settings;
        this.notifier = notifier;
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

    /** Ручной прогон по кнопке в админке: порог не учитывается, отметка анализа соблюдается. */
    public AnalysisResult analyzeChat(ChatPersona chat, int historySize) throws Exception {
        long watermark = memoryRepository.watermark(chat.chatId());
        logWindowGap(chat.chatId(), watermark, historySize);

        List<MemoryRepository.MessageRow> messages = memoryRepository.findNewMessages(chat.chatId(), watermark);
        if (messages.isEmpty())
            return new AnalysisResult(0, 0, 0, 0, 0);

        int messageLimit = readBoundedInt(AI_MEMORY_ANALYSIS_MESSAGES, DEFAULT_ANALYSIS_MESSAGES,
                1, MAX_ANALYSIS_MESSAGES);
        if (messages.size() > messageLimit) {
            logger.info("Анализ памяти в чате {}: берём последние {} из {} новых сообщений",
                    chat.chatId(), messageLimit, messages.size());
            messages = messages.subList(messages.size() - messageLimit, messages.size());
        }

        List<MemoryRecord> existing = memoryRepository.findByPersona(chat.personaId());
        ArrayNode payload = buildMessages(extractPrompt(), existing, messages);

        int maxTokens = readBoundedInt(AI_MEMORY_ANALYSIS_MAX_TOKENS, DEFAULT_ANALYSIS_MAX_TOKENS,
                1, MAX_ANALYSIS_TOKENS);
        String answer = llmClient.sendRequestToAi(payload, ANALYSIS_TEMPERATURE, maxTokens);
        if (answer == null || answer.isBlank())
            throw new IllegalStateException("Модель не вернула ответ анализа памяти");

        MemoryExtraction extraction = parser.parse(answer);
        Set<Long> protectedIds = MemoryCoreRules.ids(existing.stream()
                .filter(memory -> memory.core() || "manual".equals(memory.source()))
                .toList());
        List<Long> rejected = MemoryCoreRules.protectedDeletes(extraction.delete(), protectedIds);
        for (Long id : rejected)
            logger.warn("Анализ пытался удалить защищённое воспоминание {} (ядро или ручное) — операция отброшена", id);

        MemoryExtraction applied = new MemoryExtraction(
                extraction.add(),
                extraction.update(),
                MemoryCoreRules.filterDeletable(extraction.delete(), protectedIds));

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

        notifyOwner(chat, existing, applied, rejected, messages.size());

        return new AnalysisResult(messages.size(), applied.add().size(), applied.update().size(),
                applied.delete().size(), rejected.size());
    }

    /** Отчёт владельцу уже после записи памяти: его сбой не должен менять результат анализа. */
    private void notifyOwner(ChatPersona chat, List<MemoryRecord> existing, MemoryExtraction applied,
                             List<Long> rejected, int messages) {
        try {
            String personaName = personaRepository.findForChat(chat.chatId())
                    .map(Persona::name)
                    .orElse("");
            notifier.notifyOwner(chat, personaName, existing, applied, rejected, messages);
        } catch (Exception e) {
            logger.warn("Отчёт об обновлении памяти в чате {} не отправлен: {}",
                    chat.chatId(), String.valueOf(e));
        }
    }

    private void logWindowGap(long chatId, long watermark, int historySize) {
        // Пока анализ ни разу не проходил, «разрыва» нет: окно просто ещё не размечено.
        if (watermark <= 0)
            return;

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
        user.put("content", "Существующие воспоминания (id | категория | текст | важность | ядро | обновлено):\n"
                + existingMemories(existing)
                + "\n\nНовые сообщения:\n"
                + messages.stream()
                .map(message -> "[" + MESSAGE_TIME.format(message.createdAt()) + "] "
                        + AuthorFormat.render(message.author(), message.content()))
                .collect(Collectors.joining("\n")));
        payload.add(user);

        return payload;
    }

    private String existingMemories(List<MemoryRecord> existing) {
        if (existing.isEmpty())
            return "(нет)";
        return existing.stream()
                .map(memory -> memory.id() + " | " + memory.category() + " | " + memory.content()
                        + " | " + memory.importance() + " | " + (memory.core() ? "ядро" : "обычное")
                        + " | обновлено " + MEMORY_UPDATED.format(memory.updatedAt()))
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
        return readBoundedInt(key, defaultValue, 1, Integer.MAX_VALUE);
    }

    private int readBoundedInt(String key, int defaultValue, int min, int max) {
        String configured = settings.getSettingValue(key);
        if (configured == null)
            return defaultValue;

        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed >= min && parsed <= max)
                return parsed;
            logger.warn("Значение настройки {} '{}' вне диапазона {}..{}: используется {}",
                    key, configured, min, max, defaultValue);
        } catch (NumberFormatException e) {
            logger.warn("Некорректное значение настройки {} '{}': используется {}", key, configured, defaultValue);
        }
        return defaultValue;
    }
}
