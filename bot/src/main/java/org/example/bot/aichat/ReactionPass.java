package org.example.bot.aichat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.example.bot.MessageSender;
import org.example.bot.aichat.ReactionDecisionParser.ReactionDecision;
import org.example.core.aichat.AuthorFormat;
import org.example.core.aichat.ChatPersona;
import org.example.core.aichat.MemoryRecord;
import org.example.core.aichat.MemoryRepository;
import org.example.core.aichat.MessageReactionRepository;
import org.example.core.aichat.MessageReactionRepository.ReactionMessage;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaService;
import org.example.core.aichat.PromptComposer;
import org.example.core.aichat.PromptDefaults;
import org.example.core.aichat.ReactionEmoji;
import org.example.core.aichat.ReactionEmojiRepository;
import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.example.core.settings.Settings.AI_REACTIONS_ENABLED;
import static org.example.core.settings.Settings.AI_REACTION_MODEL;
import static org.example.core.settings.Settings.AI_REACTION_MODEL_USE_OWN;
import static org.example.core.settings.Settings.AI_REACTION_PAUSE_SECONDS;
import static org.example.core.settings.Settings.AI_REACTION_POOL_SIZE;
import static org.example.core.settings.Settings.AI_REACTION_PROMPT;
import static org.example.core.settings.Settings.AI_REACTION_ROUTE;

/**
 * Проход чтения: раз в несколько секунд смотрит чаты с персоной, накапливает
 * новые сообщения и одним запросом к модели решает, на что поставить 0–2 реакции.
 * Настроение, обиды и память не меняет; при сбое молчит в чате и пишет в лог.
 */
@Component
public class ReactionPass {
    private static final Logger logger = LoggerFactory.getLogger(ReactionPass.class);

    static final int DEFAULT_POOL_SIZE = 20;
    static final int MAX_POOL_SIZE = 100;
    static final int DEFAULT_PAUSE_SECONDS = 30;
    static final int MAX_PAUSE_SECONDS = 3600;
    static final int REACTION_MAX_TOKENS = 500;
    static final float REACTION_TEMPERATURE = 0.2f;
    static final int MAX_FACTS_PER_AUTHOR = 3;
    static final int MAX_AUTHORS_WITH_FACTS = 5;
    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final PersonaRepository personaRepository;
    private final MessageReactionRepository messages;
    private final ReactionEmojiRepository emojis;
    private final PersonaService personaService;
    private final PromptComposer composer;
    private final PersonaGrudgeService grudges;
    private final MemoryRepository memoryRepository;
    private final LlmClient llmClient;
    private final SettingsService settings;
    private final MessageSender sender;
    private final ReactionDecisionParser parser;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "reaction-pass");
        thread.setDaemon(true);
        return thread;
    });

    public ReactionPass(PersonaRepository personaRepository, MessageReactionRepository messages,
                        ReactionEmojiRepository emojis, PersonaService personaService,
                        PromptComposer composer, PersonaGrudgeService grudges,
                        MemoryRepository memoryRepository, LlmClient llmClient,
                        SettingsService settings, MessageSender sender,
                        ReactionDecisionParser parser, Clock clock) {
        this.personaRepository = personaRepository;
        this.messages = messages;
        this.emojis = emojis;
        this.personaService = personaService;
        this.composer = composer;
        this.grudges = grudges;
        this.memoryRepository = memoryRepository;
        this.llmClient = llmClient;
        this.settings = settings;
        this.sender = sender;
        this.parser = parser;
        this.clock = clock;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Проверка раз в 10 секунд: сама модель вызывается уже в отдельном пуле потоков. */
    @Scheduled(fixedDelayString = "PT10S")
    public void scheduleDueChats() {
        if (!enabled())
            return;

        for (ChatPersona chat : personaRepository.findAssignedChats())
            consider(chat);
    }

    private void consider(ChatPersona chat) {
        long chatId = chat.chatId();
        try {
            long readState = messages.readState(chatId);
            if (readState == 0) {
                // Первое включение: история не должна получить реакции задним числом.
                messages.saveReadState(chatId, messages.maxMessageId(chatId));
                return;
            }

            int poolSize = poolSize();
            List<ReactionMessage> batch = messages.findNewMessages(chatId, readState, poolSize);
            if (!due(batch, poolSize, pauseSeconds(), clock.instant()))
                return;

            if (!running.add(chatId))
                return;

            executor.submit(() -> {
                try {
                    runPass(chat);
                } catch (Exception e) {
                    logger.error("Проход чтения чата {} завершился ошибкой: {}", chatId, String.valueOf(e));
                } finally {
                    running.remove(chatId);
                }
            });
        } catch (Exception e) {
            logger.error("Ошибка проверки прохода чтения в чате {}: {}", chatId, String.valueOf(e));
        }
    }

    /** Пачка готова, если набрался размер пула или после последнего сообщения прошла пауза. */
    static boolean due(List<ReactionMessage> batch, int poolSize, int pauseSeconds, Instant now) {
        if (batch.isEmpty())
            return false;
        if (batch.size() >= poolSize)
            return true;

        Instant last = batch.get(batch.size() - 1).createdAt();
        return Duration.between(last, now).getSeconds() >= pauseSeconds;
    }

    /** Один проход: собрать пачку, спросить модель, поставить реакции и двинуть отметку. */
    void runPass(ChatPersona chat) {
        long started = System.nanoTime();
        long chatId = chat.chatId();

        long readState = messages.readState(chatId);
        List<ReactionMessage> batch = messages.findNewMessages(chatId, readState, poolSize());
        if (batch.isEmpty())
            return;

        List<ReactionEmoji> catalog = emojis.findUsableForEmotion();
        if (catalog.isEmpty()) {
            // Нечего предлагать модели: это настройка, а не сбой. Отметку не двигаем,
            // чтобы реакции появились, когда каталог поправят.
            logger.warn("Проход чтения чата {}: нет включённых эмодзи с текстом — проход пропущен", chatId);
            return;
        }

        long lastMessageId = batch.get(batch.size() - 1).id();
        int pending = messages.countNewMessages(chatId, readState);
        if (pending > batch.size())
            logger.info("Проход чтения чата {}: {} старых сообщений из {} помечены прочитанными без реакции",
                    chatId, pending - batch.size(), pending);

        try {
            String raw = llmClient.sendJsonRequestToAi(reactionModel(), reactionRoute(),
                    payload(chat, batch, catalog), REACTION_TEMPERATURE, REACTION_MAX_TOKENS);
            if (raw == null || raw.isBlank()) {
                logger.warn("Проход чтения чата {}: модель не ответила, пачка пропущена", chatId);
                messages.saveReadState(chatId, lastMessageId);
                return;
            }

            Set<Integer> messageIds = batch.stream()
                    .map(ReactionMessage::tgMessageId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> allowedEmojis = catalog.stream()
                    .map(ReactionEmoji::emoji)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<ReactionDecision> decisions = parser.parse(raw, messageIds, allowedEmojis);
            int sent = 0;
            for (ReactionDecision decision : decisions) {
                if (!sender.react(chatId, decision.messageId(), decision.emoji()))
                    continue;
                messages.saveReaction(chatId, decision.messageId(), decision.emoji(), decision.reason());
                sent++;
            }

            messages.saveReadState(chatId, lastMessageId);
            logger.info("Проход чтения чата {} за {} мс: сообщений {}, реакций {}",
                    chatId, (System.nanoTime() - started) / 1_000_000, batch.size(), sent);
        } catch (Exception e) {
            // Сбой не должен повторяться каждые 10 секунд на одной и той же пачке.
            messages.saveReadState(chatId, lastMessageId);
            logger.error("Проход чтения чата {} не удался, пачка помечена прочитанной: {}",
                    chatId, String.valueOf(e));
        }
    }

    private ArrayNode payload(ChatPersona chat, List<ReactionMessage> batch, List<ReactionEmoji> catalog) {
        ArrayNode payload = objectMapper.createArrayNode();
        payload.add(message("system", systemPrompt(chat.chatId(), catalog)));
        payload.add(message("user", userPrompt(chat, batch)));
        return payload;
    }

    /** Неизменяемая часть: характер, правила и каталог — первыми, чтобы работал кэш провайдера. */
    private String systemPrompt(long chatId, List<ReactionEmoji> catalog) {
        String persona = personaService.resolvePrompt(chatId)
                .map(PersonaService.ResolvedPrompt::text)
                .filter(text -> !text.isBlank())
                .orElse(PromptDefaults.PERSONA_FALLBACK);

        String catalogText = catalog.stream()
                .map(emoji -> emoji.emoji() + " — " + emoji.meaning())
                .collect(Collectors.joining("\n"));

        return persona + "\n\n" + reactionPrompt() + "\n\nДоступные эмодзи:\n" + catalogText;
    }

    private String userPrompt(ChatPersona chat, List<ReactionMessage> batch) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(composer.composeForPlan(chat.chatId()).text()).append("\n\n");
        prompt.append("Обиды выше порога:\n").append(grudgeList(chat.chatId())).append("\n\n");
        prompt.append("Что ты знаешь об авторах:\n").append(authorFacts(chat.personaId(), batch)).append("\n\n");
        prompt.append("Новые сообщения (id | время | автор):\n");
        prompt.append(batch.stream()
                .map(message -> "[id=" + message.tgMessageId() + "] "
                        + MESSAGE_TIME.format(message.createdAt().atZone(clock.getZone())) + " "
                        + AuthorFormat.render(message.author(), message.content()))
                .collect(Collectors.joining("\n")));
        prompt.append("\n\nВерни только JSON.");
        return prompt.toString();
    }

    private String grudgeList(long chatId) {
        List<PersonaGrudgeService.Grudge> active = grudges.targeted(chatId);
        if (active.isEmpty())
            return "(нет)";

        return active.stream()
                .map(grudge -> "- " + grudge.title() + ", сила " + grudge.strength())
                .collect(Collectors.joining("\n"));
    }

    /** По несколько фактов о каждом авторе пачки; дальше пяти авторов не разбираем. */
    private String authorFacts(int personaId, List<ReactionMessage> batch) {
        Map<Long, String> authors = new LinkedHashMap<>();
        for (ReactionMessage message : batch) {
            if (message.author() == null || message.author().userId() == null)
                continue;
            if (!authors.containsKey(message.author().userId()) && authors.size() >= MAX_AUTHORS_WITH_FACTS)
                continue;
            authors.putIfAbsent(message.author().userId(), AuthorFormat.sign(message.author()));
        }
        if (authors.isEmpty())
            return "(нет)";

        StringBuilder facts = new StringBuilder();
        for (Map.Entry<Long, String> author : authors.entrySet()) {
            List<MemoryRecord> memories = memoryRepository.findForPerson(
                    personaId, author.getKey(), MAX_FACTS_PER_AUTHOR);
            if (memories.isEmpty())
                continue;
            facts.append("- ").append(author.getValue()).append(": ")
                    .append(memories.stream().map(MemoryRecord::content).collect(Collectors.joining("; ")))
                    .append('\n');
        }
        return facts.isEmpty() ? "(нет)" : facts.toString().strip();
    }

    private ObjectNode message(String role, String content) {
        return objectMapper.createObjectNode().put("role", role).put("content", content);
    }

    private boolean enabled() {
        return settings.getBoolean(AI_REACTIONS_ENABLED, false);
    }

    private int poolSize() {
        return bounded(settings.getInt(AI_REACTION_POOL_SIZE, DEFAULT_POOL_SIZE),
                1, MAX_POOL_SIZE, DEFAULT_POOL_SIZE);
    }

    private int pauseSeconds() {
        return bounded(settings.getInt(AI_REACTION_PAUSE_SECONDS, DEFAULT_PAUSE_SECONDS),
                1, MAX_PAUSE_SECONDS, DEFAULT_PAUSE_SECONDS);
    }

    private String reactionPrompt() {
        String configured = settings.getSettingValue(AI_REACTION_PROMPT);
        return configured == null || configured.isBlank() ? PromptDefaults.REACTION_PROMPT : configured;
    }

    /** Своя пара включается флажком: без него пустая модель означает основную. */
    private String reactionModel() {
        return settings.getBoolean(AI_REACTION_MODEL_USE_OWN, false)
                ? settings.getSettingValue(AI_REACTION_MODEL)
                : "";
    }

    private String reactionRoute() {
        return settings.getBoolean(AI_REACTION_MODEL_USE_OWN, false)
                ? settings.getSettingValue(AI_REACTION_ROUTE)
                : "";
    }

    private int bounded(int value, int min, int max, int fallback) {
        if (value >= min && value <= max)
            return value;
        logger.warn("Значение настройки реакций {} вне диапазона {}..{}: используется {}", value, min, max, fallback);
        return fallback;
    }
}
