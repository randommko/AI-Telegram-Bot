package org.example.AiChat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.example.Settings.Settings.AI_PLAN_MAX_TOKENS;
import static org.example.Settings.Settings.AI_SYSTEM_ROLE;
import static org.example.Settings.Settings.USER_ROLE;

/**
 * Первый шаг ответа: модель получает собранный промпт и историю, а возвращает
 * строгий план — намерение, воспоминания, сдвиги эмоций и число реплик.
 */
@Component
public class AnswerPlanner {
    private static final Logger logger = LoggerFactory.getLogger(AnswerPlanner.class);

    static final float PLAN_TEMPERATURE = 0.2f;
    static final int DEFAULT_PLAN_MAX_TOKENS = 1500;
    static final int MAX_ALLOWED_TOKENS = 8192;

    private final PromptComposer composer;
    private final AiHistory history;
    private final DeepSeekClient deepSeekClient;
    private final SettingsService settings;
    private final PersonaGrudgeService grudges;
    private final AnswerPlanParser parser;

    public AnswerPlanner(PromptComposer composer, AiHistory history, DeepSeekClient deepSeekClient,
                         SettingsService settings, PersonaGrudgeService grudges, AnswerPlanParser parser) {
        this.composer = composer;
        this.history = history;
        this.deepSeekClient = deepSeekClient;
        this.settings = settings;
        this.grudges = grudges;
        this.parser = parser;
    }

    public Optional<AnswerPlan> plan(long chatId, int maxReplies) {
        long started = System.nanoTime();

        ArrayNode messages = history.messages(chatId);
        messages.insert(0, history.message(AI_SYSTEM_ROLE, planInstruction(chatId, maxReplies)));
        messages.insert(0, history.message(AI_SYSTEM_ROLE, composer.composeForPlan(chatId).text()));
        // Инструкция последним сообщением: модель отвечает на неё, а не продолжает беседу.
        messages.add(history.message(USER_ROLE,
                "Составь план ответа на последнее сообщение собеседника. Верни только JSON."));

        String raw = deepSeekClient.sendRequestToAi(messages, PLAN_TEMPERATURE, planMaxTokens());
        Optional<AnswerPlan> plan = parser.parse(raw, maxReplies);

        long millis = (System.nanoTime() - started) / 1_000_000;
        if (plan.isEmpty()) {
            logger.warn("Шаг плана не удался за {} мс: фолбэк на один запрос", millis);
            return Optional.empty();
        }

        AnswerPlan value = plan.get();
        logger.info("Шаг плана за {} мс: тема '{}', намерение {}, реплик {}, эмоции {} — {}",
                millis, value.topic(), value.intent().code(), value.replies(),
                value.emotionDeltas(), value.emotionReason());
        return plan;
    }

    private String planInstruction(long chatId, int maxReplies) {
        return "Ты планировщик ответа, а не персона. Не отвечай собеседнику и не пиши в его стиле.\n"
                + "Верни ТОЛЬКО JSON-объект без пояснений и без текста вокруг:\n"
                + "{\"topic\": \"тема короткой фразой\", \"intent\": \"question|remark|insult|apology|ignore\","
                + " \"memory_ids\": [1, 2], \"emotion\": {\"гнев\": 2, \"радость\": -1},"
                + " \"emotion_reason\": \"почему так\", \"replies\": 1, \"targets\": [\"@ник\"]}\n"
                + "intent — что сделал собеседник: вопрос, замечание, оскорбление в адрес персоны,"
                + " извинение или ничего, на что не нужно отвечать.\n"
                + "emotion — сдвиги значений эмоций по кодам справочника, каждое от -3 до 3.\n"
                + "replies — сколько реплик отправить, от 1 до " + maxReplies + "; для ignore — 0.\n"
                + "targets — ники из списка людей, кому адресованы колкости.\n"
                + "memory_ids — id воспоминаний, которые приложить к ответу.\n\n"
                + "Память (id | категория | текст):\n" + memoryCatalog(chatId) + "\n\n"
                + "Обиды выше порога:\n" + grudgeList(chatId) + "\n";
    }

    private String memoryCatalog(long chatId) {
        PromptComposer.MemoryPool pool = composer.memoryPool(chatId);
        if (pool.core().isEmpty() && pool.regular().isEmpty())
            return "(нет)";

        StringBuilder catalog = new StringBuilder();
        appendMemories(catalog, pool.core());
        appendMemories(catalog, pool.regular());
        return catalog.toString().strip();
    }

    private static void appendMemories(StringBuilder catalog, List<MemoryRecord> memories) {
        for (MemoryRecord memory : memories) {
            catalog.append(memory.id()).append(" | ").append(memory.category())
                    .append(" | ").append(memory.content()).append('\n');
        }
    }

    private String grudgeList(long chatId) {
        List<PersonaGrudgeService.Grudge> active = grudges.targeted(chatId);
        if (active.isEmpty())
            return "(нет)";

        StringBuilder list = new StringBuilder();
        for (PersonaGrudgeService.Grudge grudge : active)
            list.append("- ").append(grudge.title()).append(", сила ").append(grudge.strength()).append('\n');
        return list.toString().strip();
    }

    private int planMaxTokens() {
        int configured = settings.getInt(AI_PLAN_MAX_TOKENS, DEFAULT_PLAN_MAX_TOKENS);
        if (configured < 1 || configured > MAX_ALLOWED_TOKENS) {
            logger.warn("Лимит токенов плана {} вне диапазона 1..{}: используется {}",
                    configured, MAX_ALLOWED_TOKENS, DEFAULT_PLAN_MAX_TOKENS);
            return DEFAULT_PLAN_MAX_TOKENS;
        }
        return configured;
    }
}
