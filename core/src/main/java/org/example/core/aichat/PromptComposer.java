package org.example.core.aichat;

import org.example.core.settings.SettingsService;
import org.example.core.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.example.core.settings.Settings.AI_PROMPT_ANSWER_FORMAT;
import static org.example.core.settings.Settings.AI_PROMPT_EMOTIONS_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_MEMORY_CORE_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_MEMORY_REGULAR_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_PEOPLE_HEADER;
import static org.example.core.settings.Settings.AI_PROMPT_TECHNICAL;
import static org.example.core.settings.Settings.AI_PROMPT_TIME_TEMPLATE;
import static org.example.core.settings.Settings.AI_PROMPT_TIME_ZONE;
import static org.example.core.settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.core.settings.Settings.USER_ROLE;

/**
 * Собирает системный промпт из разделов в фиксированном порядке. Пустой раздел
 * пропускается вместе с заголовком; настройки читаются на каждый вызов, поэтому
 * правки в админке действуют со следующего ответа.
 */
@Component
public class PromptComposer {
    private static final Logger logger = LoggerFactory.getLogger(PromptComposer.class);

    static final int PEOPLE_MESSAGES = 50;
    static final int MAX_PEOPLE_LINES = 10;
    static final int DEFAULT_MEMORY_PROMPT_SIZE = 30;
    /** Пул кандидатов: события автора триггера и по десять записей каждого другого типа. */
    static final int POOL_EVENTS_PER_AUTHOR = 15;
    static final int POOL_PER_CATEGORY = 10;
    /** Страховка от роста памяти: больше этого пул в план не отдаётся. */
    static final int POOL_MAX_CHARS = 30_000;
    /** Предупреждение, а не обрезка: резать правила поведения опаснее, чем передать их целиком. */
    static final int TECHNICAL_BUDGET = 800;
    /** Промпт Юры с примерами — 7,2k символов, поэтому бюджет поднят, чтобы не писать WARN на каждый ответ. */
    static final int PERSONA_BUDGET = 8000;

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", RU);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", RU);

    private final SettingsService settings;
    private final PersonaService personaService;
    private final EmotionService emotionService;
    private final MemoryRepository memoryRepository;
    private final MemoryPromptBuilder memoryPromptBuilder;
    private final AiChatRepository aiChatRepository;
    private final MemoryDecay memoryDecay;
    private final Clock clock;

    public PromptComposer(SettingsService settings, PersonaService personaService, EmotionService emotionService,
                          MemoryRepository memoryRepository, MemoryPromptBuilder memoryPromptBuilder,
                          AiChatRepository aiChatRepository, MemoryDecay memoryDecay, Clock clock) {
        this.settings = settings;
        this.personaService = personaService;
        this.emotionService = emotionService;
        this.memoryRepository = memoryRepository;
        this.memoryPromptBuilder = memoryPromptBuilder;
        this.aiChatRepository = aiChatRepository;
        this.memoryDecay = memoryDecay;
        this.clock = clock;
    }

    /** Раздел промпта: спецификация (заголовок, бюджет) и уже собранный текст. */
    public record Section(PromptSection spec, String body) {
    }

    public record ComposedPrompt(List<Section> sections, String text) {
    }

    /** Ядро и обычные воспоминания из топ-N: план выбирает из них, что приложить к ответу. */
    public record MemoryPool(List<MemoryRecord> core, List<MemoryRecord> regular) {
    }

    public ComposedPrompt compose(long chatId) {
        return compose(chatId, false, null);
    }

    /** readOnly — для предпросмотра: чтение эмоций не добивает недостающие строки в базе. */
    public ComposedPrompt compose(long chatId, boolean readOnly) {
        return compose(chatId, readOnly, null);
    }

    /** compose с выбором обычных воспоминаний: пустой выбор означает все топ-N. */
    public ComposedPrompt compose(long chatId, Collection<Integer> regularMemoryIds) {
        return compose(chatId, false, regularMemoryIds);
    }

    private ComposedPrompt compose(long chatId, boolean readOnly, Collection<Integer> regularMemoryIds) {
        Optional<Persona> persona = personaFor(chatId);

        List<Section> sections = new ArrayList<>();
        add(sections, PromptSection.TECHNICAL, applyLength(text(AI_PROMPT_TECHNICAL, PromptDefaults.TECHNICAL)));
        add(sections, PromptSection.PERSONA, personaText(chatId));
        add(sections, PromptSection.EMOTIONS, emotions(persona, readOnly));
        add(sections, PromptSection.MEMORY, memories(persona, regularMemoryIds));
        add(sections, PromptSection.PEOPLE, people(chatId));
        add(sections, PromptSection.TIME, time());
        add(sections, PromptSection.ANSWER_FORMAT,
                applyLength(text(AI_PROMPT_ANSWER_FORMAT, PromptDefaults.ANSWER_FORMAT)));

        String prompt = String.join("\n\n", sections.stream().map(PromptComposer::render).toList());
        logger.info("Системный промпт чата {}: персона {}, разделов {}, символов {}",
                chatId, persona.map(Persona::name).orElse("без персоны"), sections.size(), prompt.length());
        return new ComposedPrompt(List.copyOf(sections), prompt);
    }

    /** Каталог памяти для плана без автора: ядро и пул по правилу, без записи в базу. */
    public MemoryPool memoryPool(long chatId) {
        return memoryPool(chatId, null);
    }

    /**
     * Пул кандидатов для плана: топ-15 событий автора триггера и по топ-10 каждого
     * другого типа. Порядок — по действующей важности: событие со временем дешевеет.
     */
    public MemoryPool memoryPool(long chatId, Long authorUserId) {
        Optional<Persona> persona = personaFor(chatId);
        if (persona.isEmpty())
            return new MemoryPool(List.of(), List.of());

        try {
            int personaId = persona.get().id();
            int daysPerPoint = memoryDecay.daysPerPoint();
            Instant now = clock.instant();

            List<MemoryRecord> core = memoryRepository.findCore(personaId);
            List<MemoryRecord> events = eventsForAuthor(personaId, authorUserId, daysPerPoint, now);
            List<MemoryRecord> regular = regularByCategory(personaId, daysPerPoint, now);
            List<MemoryRecord> pool = capByChars(personaId, events, regular);

            logger.info("Пул памяти персоны {}: событий {}, по типам {}, символов {}",
                    personaId, events.size(), regular.size(),
                    pool.stream().mapToInt(memory -> memory.content() == null ? 0
                            : memory.content().length()).sum());
            return new MemoryPool(core, pool);
        } catch (Exception e) {
            logger.error("Ошибка чтения пула памяти персоны {}: {}", persona.get().id(), String.valueOf(e));
            return new MemoryPool(List.of(), List.of());
        }
    }

    /** События автора; если у него их нет — общие, чтобы пул не остался пустым. */
    private List<MemoryRecord> eventsForAuthor(int personaId, Long authorUserId, int daysPerPoint, Instant now) {
        List<MemoryRecord> events = memoryRepository.findEvents(personaId, authorUserId);
        if (events.isEmpty() && authorUserId != null) {
            logger.info("У автора {} нет событий в памяти персоны {}: взяты общие", authorUserId, personaId);
            events = memoryRepository.findEvents(personaId, null);
        }
        return ranked(events, POOL_EVENTS_PER_AUTHOR, daysPerPoint, now);
    }

    /** По десять записей каждого типа: типы с меньшим числом отдают все, что есть. */
    private List<MemoryRecord> regularByCategory(int personaId, int daysPerPoint, Instant now) {
        Map<String, List<MemoryRecord>> byCategory = new LinkedHashMap<>();
        for (MemoryRecord memory : memoryRepository.findRegular(personaId))
            byCategory.computeIfAbsent(memory.category(), key -> new ArrayList<>()).add(memory);

        List<MemoryRecord> result = new ArrayList<>();
        for (List<MemoryRecord> group : byCategory.values())
            result.addAll(ranked(group, POOL_PER_CATEGORY, daysPerPoint, now));
        return result;
    }

    private List<MemoryRecord> ranked(List<MemoryRecord> memories, int limit, int daysPerPoint, Instant now) {
        return memories.stream()
                .sorted(byEffectiveImportance(daysPerPoint, now))
                .limit(limit)
                .toList();
    }

    private Comparator<MemoryRecord> byEffectiveImportance(int daysPerPoint, Instant now) {
        return Comparator
                .comparingInt((MemoryRecord memory) -> memoryDecay.effective(memory, daysPerPoint, now))
                .reversed()
                .thenComparing(MemoryRecord::eventDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MemoryRecord::id, Comparator.reverseOrder());
    }

    /** Бюджет пула в символах: лишние записи отбрасываются с предупреждением. */
    private List<MemoryRecord> capByChars(int personaId, List<MemoryRecord> events, List<MemoryRecord> regular) {
        List<MemoryRecord> candidates = new ArrayList<>(events);
        candidates.addAll(regular);

        List<MemoryRecord> kept = new ArrayList<>();
        int chars = 0;
        for (MemoryRecord memory : candidates) {
            int length = memory.content() == null ? 0 : memory.content().length();
            if (chars + length > POOL_MAX_CHARS) {
                logger.warn("Пул памяти персоны {} урезан бюджетом {} символов: отброшено {} записей",
                        personaId, POOL_MAX_CHARS, candidates.size() - kept.size());
                break;
            }
            chars += length;
            kept.add(memory);
        }
        return kept;
    }

    /**
     * Контекст для шага «план»: без характера, техники и формата ответа.
     * Полный промпт персоны заставляет модель отвечать в роли вместо JSON.
     * Память сюда не входит: план получает каталог с идентификаторами, чтобы выбрать из него.
     */
    public ComposedPrompt composeForPlan(long chatId) {
        Optional<Persona> persona = personaFor(chatId);

        List<Section> sections = new ArrayList<>();
        add(sections, PromptSection.EMOTIONS, emotions(persona, true));
        add(sections, PromptSection.PEOPLE, people(chatId));
        add(sections, PromptSection.TIME, time());

        String prompt = String.join("\n\n", sections.stream().map(PromptComposer::render).toList());
        logger.info("Контекст плана чата {}: разделов {}, символов {}", chatId, sections.size(), prompt.length());
        return new ComposedPrompt(List.copyOf(sections), prompt);
    }

    private static String render(Section section) {
        String heading = section.spec().heading();
        return heading == null ? section.body() : heading + "\n" + section.body();
    }

    private void add(List<Section> sections, PromptSection spec, String body) {
        if (body == null || body.isBlank())
            return;

        String value = body.strip();
        int length = value.length();
        if (spec == PromptSection.TECHNICAL && length > TECHNICAL_BUDGET)
            logger.warn("Техническая часть длиннее бюджета: {} из {} символов", length, TECHNICAL_BUDGET);
        if (spec == PromptSection.PERSONA && length > PERSONA_BUDGET)
            logger.warn("Промпт персоны длиннее {} символов: {}", PERSONA_BUDGET, length);

        logger.info("Раздел «{}»: {} символов", spec.title(), length);
        sections.add(new Section(spec, value));
    }

    private Optional<Persona> personaFor(long chatId) {
        try {
            return personaService.personaForChat(chatId);
        } catch (Exception e) {
            logger.error("Ошибка чтения персоны чата {}: {}", chatId, String.valueOf(e));
            return Optional.empty();
        }
    }

    private String personaText(long chatId) {
        try {
            Optional<PersonaService.ResolvedPrompt> resolved = personaService.resolvePrompt(chatId);
            if (resolved.isPresent() && !resolved.get().text().isBlank())
                return resolved.get().text();
        } catch (Exception e) {
            logger.error("Ошибка выбора промпта для чата {}: {}", chatId, String.valueOf(e));
        }
        logger.info("В чате {} использован промпт по умолчанию", chatId);
        return PromptDefaults.PERSONA_FALLBACK;
    }

    /** Раздел «Эмоции» ровно в том виде, в каком он попадает в системный промпт. */
    public String emotionsSection(EmotionState state) {
        if (state == null)
            return "";

        String body = emotionsBody(EmotionService.bodyText(state)).strip();
        return body.isBlank() ? "" : render(new Section(PromptSection.EMOTIONS, body));
    }

    private String emotions(Optional<Persona> persona, boolean readOnly) {
        if (persona.isEmpty())
            return "";

        try {
            return emotionsBody(emotionService.bodyFor(persona.get().id(), readOnly));
        } catch (Exception e) {
            logger.error("Ошибка сборки настроения для персоны {}: {}", persona.get().id(), String.valueOf(e));
            return "";
        }
    }

    /** Тело раздела: заголовок из настроек и строки состояния либо строка о ровном состоянии. */
    private String emotionsBody(String stateText) {
        if (stateText == null || stateText.isBlank())
            return "";
        // Ровное состояние — одна строка, заголовок к ней не добавляется.
        if (EmotionService.CALM_LINE.equals(stateText))
            return stateText;
        return text(AI_PROMPT_EMOTIONS_HEADER, PromptDefaults.EMOTIONS_HEADER) + "\n" + stateText;
    }

    private String memories(Optional<Persona> persona, Collection<Integer> regularMemoryIds) {
        if (persona.isEmpty())
            return "";

        try {
            int personaId = persona.get().id();
            List<MemoryRecord> core = memoryRepository.findCore(personaId);
            List<MemoryRecord> regular = selectedRegular(personaId, regularMemoryIds);

            String block = memoryPromptBuilder.build(
                    core, regular,
                    text(AI_PROMPT_MEMORY_CORE_HEADER, PromptDefaults.MEMORY_CORE_HEADER),
                    text(AI_PROMPT_MEMORY_REGULAR_HEADER, PromptDefaults.MEMORY_REGULAR_HEADER));
            logger.info("Память персоны {}: ядровых {}, обычных {}, символов {}",
                    personaId, core.size(), regular.size(), block.length());
            return block;
        } catch (Exception e) {
            logger.error("Ошибка сборки памяти персоны {}: {}", persona.get().id(), String.valueOf(e));
            return "";
        }
    }

    /**
     * Выбранное планом тянется по id: раньше выбор искали только среди топ-N,
     * и запись за его пределами терялась молча.
     */
    private List<MemoryRecord> selectedRegular(int personaId, Collection<Integer> ids) {
        if (ids == null || ids.isEmpty())
            return memoryRepository.findTopRegular(personaId, memoryPromptSize());

        List<MemoryRecord> found = memoryRepository.findByIds(personaId, ids);
        if (found.isEmpty()) {
            // Пустой или неизвестный выбор не должен оставить генерацию без памяти.
            logger.warn("План выбрал воспоминания {}, но ни одно не найдено у персоны {}: приложены все обычные из топ-N",
                    ids, personaId);
            return memoryRepository.findTopRegular(personaId, memoryPromptSize());
        }
        if (found.size() < ids.size())
            logger.warn("План выбрал {} воспоминаний, найдено {} у персоны {}: неизвестные идентификаторы отброшены",
                    ids.size(), found.size(), personaId);

        // Ядро приходит отдельным списком: выбранное ядро не дублируется.
        return found.stream().filter(memory -> !memory.core()).toList();
    }

    /**
     * Люди берутся из авторов последних сообщений, а не из справочника: таблицы
     * участников чата нет, tg_users хранит всех, кого бот когда-либо видел.
     */
    private String people(long chatId) {
        try {
            List<AiMessage> recent = aiChatRepository.findLastMessages(chatId, PEOPLE_MESSAGES);

            Map<String, MessageAuthor> authors = new LinkedHashMap<>();
            MessageAuthor current = null;
            // От новых к старым: в разделе первыми идут самые свежие авторы.
            for (int i = recent.size() - 1; i >= 0; i--) {
                AiMessage message = recent.get(i);
                if (!USER_ROLE.equals(message.role()) || message.author() == null || message.author().isEmpty())
                    continue;
                if (current == null)
                    current = message.author();
                authors.putIfAbsent(key(message.author()), message.author());
            }

            if (authors.isEmpty())
                return "";

            List<MessageAuthor> people = new ArrayList<>(authors.values());
            if (people.size() > MAX_PEOPLE_LINES) {
                logger.warn("В чате {} авторов больше лимита: отброшено {}", chatId, people.size() - MAX_PEOPLE_LINES);
                people = people.subList(0, MAX_PEOPLE_LINES);
            }

            List<String> lines = new ArrayList<>();
            for (MessageAuthor author : people)
                lines.add("- " + AuthorFormat.sign(author) + (same(author, current) ? " — сейчас пишет" : ""));

            return text(AI_PROMPT_PEOPLE_HEADER, PromptDefaults.PEOPLE_HEADER) + "\n" + String.join("\n", lines);
        } catch (Exception e) {
            logger.error("Ошибка сборки списка людей чата {}: {}", chatId, String.valueOf(e));
            return "";
        }
    }

    private String time() {
        ZoneId zone = zone();
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), zone);
        return text(AI_PROMPT_TIME_TEMPLATE, PromptDefaults.TIME_TEMPLATE)
                .replace("{date}", DATE.format(now))
                .replace("{time}", TIME.format(now))
                .replace("{zone}", zone.getId())
                .replace("{weekday}", WEEKDAY.format(now));
    }

    private ZoneId zone() {
        String configured = settings.getSettingValue(AI_PROMPT_TIME_ZONE);
        if (configured == null || configured.isBlank())
            return ZoneId.of(PromptDefaults.TIME_ZONE);

        try {
            return ZoneId.of(configured.trim());
        } catch (DateTimeException e) {
            logger.warn("Некорректный часовой пояс '{}': используется {}", configured, PromptDefaults.TIME_ZONE);
            return ZoneId.of(PromptDefaults.TIME_ZONE);
        }
    }

    private static boolean same(MessageAuthor left, MessageAuthor right) {
        if (left == null || right == null)
            return false;
        if (left.userId() != null && right.userId() != null)
            return left.userId().equals(right.userId());
        return AuthorFormat.sign(left).equals(AuthorFormat.sign(right));
    }

    private static String key(MessageAuthor author) {
        return author.userId() != null ? "id:" + author.userId() : "sign:" + AuthorFormat.sign(author);
    }

    private static String applyLength(String template) {
        return template.replace("{maxLength}", String.valueOf(TextSplitter.MAX_MESSAGE_LENGTH));
    }

    private String text(String key, String fallback) {
        String configured = settings.getSettingValue(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private int memoryPromptSize() {
        String configuredLimit = settings.getSettingValue(AI_MEMORY_PROMPT_SIZE);
        if (configuredLimit == null)
            return DEFAULT_MEMORY_PROMPT_SIZE;

        try {
            int limit = Integer.parseInt(configuredLimit.trim());
            // Ноль — допустимое значение: в промпт идёт только ядро.
            if (limit >= 0)
                return limit;
            logger.warn("Некорректный размер памяти в промпте '{}': используется {}",
                    configuredLimit, DEFAULT_MEMORY_PROMPT_SIZE);
        } catch (NumberFormatException e) {
            logger.warn("Некорректный размер памяти в промпте '{}': используется {}",
                    configuredLimit, DEFAULT_MEMORY_PROMPT_SIZE);
        }
        return DEFAULT_MEMORY_PROMPT_SIZE;
    }
}
