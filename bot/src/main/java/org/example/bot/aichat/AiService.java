package org.example.bot.aichat;

import org.example.core.aichat.AiChatRepository;
import org.example.core.aichat.AiHistory;
import org.example.core.aichat.AiMessage;
import org.example.core.aichat.AuthorFormat;
import org.example.core.aichat.EmotionService;
import org.example.core.aichat.EmotionState;
import org.example.core.aichat.MessageAuthor;
import org.example.core.aichat.PersonaRepository;
import org.example.core.aichat.PersonaSettings;
import org.example.core.aichat.PromptComposer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.bot.Delay;
import org.example.bot.MessageSender;
import org.example.core.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.example.core.settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.core.settings.Settings.AI_ASSISTANT_ROLE;
import static org.example.core.settings.Settings.AI_SYSTEM_ROLE;
import static org.example.core.settings.Settings.USER_ROLE;

@Component
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    private static final float DEFAULT_ANSWER_TEMPERATURE = 1.0f;
    private static final float MAX_ANSWER_TEMPERATURE = 2.0f;
    private static final int HYSTERIA_TARGET_MESSAGES = 50;
    static final String AI_UNAVAILABLE_NOTICE = "ИИ недоступен, попробуйте позже";

    private final SettingsService settings;
    private final MessageSender sender;
    private final LlmClient llmClient;
    private final AiChatRepository repository;
    private final PromptComposer promptComposer;
    private final AiHistory history;
    private final AnswerPlanner planner;
    private final AnswerGenerator generator;
    private final PersonaRepository personaRepository;
    private final PersonaGrudgeService grudges;
    private final EmotionService emotionService;
    private final HysteriaService hysteriaService;
    private final Delay delay;

    public AiService(SettingsService settings, MessageSender sender, LlmClient llmClient,
                     AiChatRepository repository, PromptComposer promptComposer, AiHistory history,
                     AnswerPlanner planner, AnswerGenerator generator, PersonaRepository personaRepository,
                     PersonaGrudgeService grudges, EmotionService emotionService,
                     HysteriaService hysteriaService, Delay delay) {
        this.settings = settings;
        this.sender = sender;
        this.llmClient = llmClient;
        this.repository = repository;
        this.promptComposer = promptComposer;
        this.history = history;
        this.planner = planner;
        this.generator = generator;
        this.personaRepository = personaRepository;
        this.grudges = grudges;
        this.emotionService = emotionService;
        this.hysteriaService = hysteriaService;
        this.delay = delay;
    }

    /** Запрос по слэш-команде: триггер — само сообщение команды. */
    public void askAi(Message message) {
        String[] parts = message.getText().split(" ", 2);
        String userQuestion = parts.length > 1 ? parts[1] : "";
        askAi(message.getChatId(), userQuestion, replyToMessageId(message), MessageAuthor.from(message.getFrom()));
    }

    /** Ответ без триггера: реплая нет, автор неизвестен. Нужен тестам и служебным вызовам. */
    void askAi(Long chatId, String userQuestion) {
        askAi(chatId, userQuestion, null, MessageAuthor.none());
    }

    /**
     * @param triggerMessageId сообщение, вызвавшее ответ; реплай ставится на него,
     * @param author          автор этого сообщения — к нему применяются обида и эмоции
     */
    public void askAi(Long chatId, String userQuestion, Integer triggerMessageId, MessageAuthor author) {
        if (userQuestion == null || userQuestion.isBlank()) {
            sender.sendMessage(chatId, "Напиши свой вопрос после команды /ai");
            return;
        }

        MessageAuthor triggerAuthor = author == null ? MessageAuthor.none() : author;

        Optional<PersonaSettings> configured = personaRepository.findSettingsForChat(chatId);
        if (configured.isEmpty()) {
            // Чат без персоны живёт по прежним правилам: один запрос и один текст.
            fallback(chatId, triggerMessageId);
            return;
        }

        PersonaSettings persona = configured.get();
        Optional<AnswerPlan> planned = planner.plan(chatId, persona.maxReplies(), triggerAuthor);
        if (planned.isEmpty()) {
            fallback(chatId, triggerMessageId);
            return;
        }

        AnswerPlan plan = planned.get();
        if (plan.intent() == PlanIntent.IGNORE) {
            logger.info("План в чате {} решил не отвечать: {}", chatId, plan.topic());
            return;
        }

        boolean hysteria = hysteriaService.shouldStart(persona, plan.intent(), aggression(persona.personaId()));
        int replyCount = hysteria ? persona.hysteriaReplies() : plan.replies();
        List<String> targets = hysteria ? recentAuthors(chatId) : plan.targets();

        List<String> replies = generator.generate(chatId, plan, replyCount, targets, hysteria, answerTemperature());
        if (replies.isEmpty()) {
            fallback(chatId, triggerMessageId);
            return;
        }

        sendReplies(chatId, replies, persona.replyDelayMs(), triggerMessageId);
        applyOutcome(chatId, persona, plan, triggerAuthor, userQuestion, hysteria);
    }

    /** Реплай только в группах: в личном чате цитата — шум. */
    public static Integer replyToMessageId(Message message) {
        if (message == null || message.getMessageId() == null)
            return null;

        var chat = message.getChat();
        if (chat == null || !(chat.isGroupChat() || chat.isSuperGroupChat()))
            return null;

        return message.getMessageId();
    }

    public void saveMessage(Long chatId, String role, String messageToSave) {
        repository.saveMessage(chatId, role, messageToSave, history.size());
    }

    public void saveMessage(Long chatId, String role, String messageToSave, MessageAuthor author) {
        repository.saveMessage(chatId, role, messageToSave, history.size(), author);
    }

    public int getChatHistorySize(Long chatId) {
        return repository.countMessages(chatId);
    }

    public void clearChatHistory(Long chatId) {
        repository.clearMessages(chatId);
        int resets = repository.incrementResets(chatId);
        logger.info("Очищена память AI в чате {}, обнулений памяти: {}", chatId, resets);
    }

    /** Один запрос и один текст: так отвечает чат без персоны и так работает фолбэк. */
    private void fallback(Long chatId, Integer triggerMessageId) {
        ArrayNode messages = history.messages(chatId);
        messages.insert(0, history.message(AI_SYSTEM_ROLE, promptComposer.compose(chatId).text()));

        String answer = llmClient.sendRequestToAi(messages, answerTemperature());
        if (answer == null || answer.isBlank()) {
            logger.warn("Модель не ответила в чате {}: отправлено уведомление о недоступности", chatId);
            sender.sendMessage(chatId, AI_UNAVAILABLE_NOTICE, triggerMessageId);
            return;
        }

        sender.sendLongMessage(chatId, answer, triggerMessageId);
        saveMessage(chatId, AI_ASSISTANT_ROLE, answer);
    }

    private void sendReplies(Long chatId, List<String> replies, int delayMs, Integer triggerMessageId) {
        for (int i = 0; i < replies.size(); i++) {
            // Реплай только у первой реплики: остальные — продолжение того же ответа.
            Integer replyId = i == 0 ? triggerMessageId : null;
            sender.sendLongMessage(chatId, replies.get(i), replyId);
            saveMessage(chatId, AI_ASSISTANT_ROLE, replies.get(i));
            if (i < replies.size() - 1 && delayMs > 0)
                delay.pause(delayMs);
        }
    }

    /** Настроение и обиды меняются после отправки: текущий ответ говорит ещё из прошлого состояния. */
    private void applyOutcome(Long chatId, PersonaSettings persona, AnswerPlan plan,
                              MessageAuthor author, String question, boolean hysteria) {
        try {
            Map<String, Integer> deltas = new LinkedHashMap<>(plan.emotionDeltas());
            if (plan.intent() == PlanIntent.INSULT && author != null && author.userId() != null) {
                grudges.insult(chatId, author.userId(), question);
                deltas.merge(EmotionService.ANGER_CODE, grudges.angerDelta(), Integer::sum);
            }
            if (plan.intent() == PlanIntent.APOLOGY && author != null && author.userId() != null)
                grudges.forgive(chatId, author.userId());

            if (!deltas.isEmpty()) {
                Map<String, Integer> applied = emotionService.applyShifts(persona.personaId(), deltas);
                logger.info("Сдвиги настроения персоны {}: {} — {}", persona.personaId(), applied,
                        plan.emotionReason());
            }

            if (hysteria)
                hysteriaService.record(persona.personaId());
        } catch (Exception e) {
            logger.error("Ошибка применения последствий ответа в чате {}: {}", chatId, String.valueOf(e));
        }
    }

    private int aggression(int personaId) {
        EmotionState state = emotionService.stateFor(personaId);
        return state == null ? 0 : state.aggression();
    }

    /** Все, кто писал в чате последние сообщения: адресаты срыва. */
    private List<String> recentAuthors(long chatId) {
        Set<String> authors = new LinkedHashSet<>();
        for (AiMessage message : repository.findLastMessages(chatId, HYSTERIA_TARGET_MESSAGES)) {
            if (!USER_ROLE.equals(message.role()) || message.author() == null || message.author().isEmpty())
                continue;
            String title = AuthorFormat.sign(message.author());
            if (!title.isEmpty())
                authors.add(title);
        }
        return new ArrayList<>(authors);
    }

    /** Температура читается на каждый запрос: правка в админке действует без перезапуска. */
    private float answerTemperature() {
        float configured = settings.getFloat(AI_ANSWER_TEMPERATURE, DEFAULT_ANSWER_TEMPERATURE);
        if (configured < 0 || configured > MAX_ANSWER_TEMPERATURE) {
            logger.warn("Температура ответа {} вне диапазона 0..2: используется {}",
                    configured, DEFAULT_ANSWER_TEMPERATURE);
            return DEFAULT_ANSWER_TEMPERATURE;
        }
        return configured;
    }
}
