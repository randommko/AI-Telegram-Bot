package org.example.AiChat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.Delay;
import org.example.MessageSender;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_ASSISTANT_ROLE;
import static org.example.Settings.Settings.AI_SYSTEM_ROLE;
import static org.example.Settings.Settings.USER_ROLE;

@Component
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    private static final float DEFAULT_ANSWER_TEMPERATURE = 1.0f;
    private static final float MAX_ANSWER_TEMPERATURE = 2.0f;
    private static final int HYSTERIA_TARGET_MESSAGES = 50;

    private final SettingsService settings;
    private final MessageSender sender;
    private final DeepSeekClient deepSeekClient;
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

    public AiService(SettingsService settings, MessageSender sender, DeepSeekClient deepSeekClient,
                     AiChatRepository repository, PromptComposer promptComposer, AiHistory history,
                     AnswerPlanner planner, AnswerGenerator generator, PersonaRepository personaRepository,
                     PersonaGrudgeService grudges, EmotionService emotionService,
                     HysteriaService hysteriaService, Delay delay) {
        this.settings = settings;
        this.sender = sender;
        this.deepSeekClient = deepSeekClient;
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

    public void askAi(Message message) {
        String[] parts = message.getText().split(" ", 2);
        String userQuestion = parts.length > 1 ? parts[1] : "";
        askAi(message.getChatId(), userQuestion);
    }

    public void askAi(Long chatId, String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            sender.sendMessage(chatId, "Напиши свой вопрос после команды /ai");
            return;
        }

        Optional<PersonaSettings> configured = personaRepository.findSettingsForChat(chatId);
        if (configured.isEmpty()) {
            // Чат без персоны живёт по прежним правилам: один запрос и один текст.
            fallback(chatId);
            return;
        }

        PersonaSettings persona = configured.get();
        Optional<AnswerPlan> planned = planner.plan(chatId, persona.maxReplies());
        if (planned.isEmpty()) {
            fallback(chatId);
            return;
        }

        AnswerPlan plan = planned.get();
        if (plan.intent() == PlanIntent.IGNORE) {
            logger.info("План в чате {} решил не отвечать: {}", chatId, plan.topic());
            return;
        }

        MessageAuthor author = currentAuthor(chatId);
        boolean hysteria = hysteriaService.shouldStart(persona, plan.intent(), aggression(persona.personaId()));
        int replyCount = hysteria ? persona.hysteriaReplies() : plan.replies();
        List<String> targets = hysteria ? recentAuthors(chatId) : plan.targets();

        List<String> replies = generator.generate(chatId, plan, replyCount, targets, hysteria, answerTemperature());
        if (replies.isEmpty()) {
            fallback(chatId);
            return;
        }

        sendReplies(chatId, replies, persona.replyDelayMs());
        applyOutcome(chatId, persona, plan, author, userQuestion, hysteria);
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
    private void fallback(Long chatId) {
        ArrayNode messages = history.messages(chatId);
        messages.insert(0, history.message(AI_SYSTEM_ROLE, promptComposer.compose(chatId).text()));

        String answer = deepSeekClient.sendRequestToAi(messages, answerTemperature());
        if (answer == null)
            return;

        sender.sendLongMessage(chatId, answer);
        saveMessage(chatId, AI_ASSISTANT_ROLE, answer);
    }

    private void sendReplies(Long chatId, List<String> replies, int delayMs) {
        for (int i = 0; i < replies.size(); i++) {
            sender.sendLongMessage(chatId, replies.get(i));
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

    private MessageAuthor currentAuthor(long chatId) {
        List<AiMessage> last = repository.findLastMessages(chatId, 1);
        if (last.isEmpty() || !USER_ROLE.equals(last.get(0).role()))
            return MessageAuthor.none();
        return last.get(0).author();
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
