package org.example.AiChat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.example.Settings.Settings.AI_SYSTEM_ROLE;
import static org.example.Settings.Settings.USER_ROLE;

/**
 * Второй шаг ответа: модель получает собранный промпт и план, а возвращает
 * JSON-массив реплик. Сбой разбора означает фолбэк на один запрос.
 */
@Component
public class AnswerGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AnswerGenerator.class);

    private final PromptComposer composer;
    private final AiHistory history;
    private final DeepSeekClient deepSeekClient;
    private final ReplyParser parser;

    public AnswerGenerator(PromptComposer composer, AiHistory history, DeepSeekClient deepSeekClient,
                           ReplyParser parser) {
        this.composer = composer;
        this.history = history;
        this.deepSeekClient = deepSeekClient;
        this.parser = parser;
    }

    public List<String> generate(long chatId, AnswerPlan plan, int replies, List<String> targets,
                                 boolean hysteria, float temperature) {
        if (replies <= 0)
            return List.of();

        long started = System.nanoTime();
        ArrayNode messages = history.messages(chatId);
        messages.insert(0, history.message(AI_SYSTEM_ROLE,
                instruction(plan, replies, targets, hysteria)));
        messages.insert(0, history.message(AI_SYSTEM_ROLE,
                composer.compose(chatId, plan.memoryIds()).text()));
        // Финальное требование отдельным сообщением: иначе персона отвечает прозой в чат.
        messages.add(history.message(USER_ROLE,
                "Верни только JSON-объект с полем replies. Никакого текста до или после него."));

        String raw = deepSeekClient.sendJsonRequestToAi(messages, temperature);
        List<String> result = parser.parse(raw, replies);

        if (result.isEmpty()) {
            // Провайдер иногда возвращает пустой content при finish_reason=stop: один повтор дешевле фолбэка.
            logger.warn("Генерация вернула пустой ответ, повтор с уточнением");
            messages.add(history.message(USER_ROLE,
                    "Предыдущий ответ пустой. Верни JSON-объект {\"replies\": [\"реплика\"]} с репликами."));
            raw = deepSeekClient.sendJsonRequestToAi(messages, 0.2f);
            result = parser.parse(raw, replies);
        }

        long millis = (System.nanoTime() - started) / 1_000_000;
        logger.info("Шаг генерации за {} мс: реплик {}{}", millis, result.size(),
                hysteria ? " (срыв)" : "");
        return result;
    }

    private String instruction(AnswerPlan plan, int replies, List<String> targets, boolean hysteria) {
        StringBuilder text = new StringBuilder("Сейчас ты не отвечаешь в чат напрямую: ты готовишь реплики, ");
        text.append("которые бот разошлёт от твоего имени.\n");
        text.append("JSON — это техническая обёртка. Правила стиля (живая речь, без markdown, ");
        text.append("короткие фразы) относятся к строкам внутри массива, а не к самому ответу.\n");
        text.append("Верни ТОЛЬКО JSON-объект {\"replies\": [\"реплика\", \"реплика\"]}: от 1 до ")
                .append(replies).append(" строк.\n");
        text.append("План: тема '").append(plan.topic()).append("', намерение ")
                .append(plan.intent().code()).append(", реплик ").append(replies).append(".\n");
        if (!targets.isEmpty())
            text.append("Адресаты колкостей: ").append(String.join(", ", targets)).append(".\n");
        if (hysteria)
            text.append("Это срыв: отвечай всем, кого задело, не сглаживай и не извиняйся.\n");
        text.append("Не добавляй пояснений вне массива, не нумеруй реплики.");
        return text.toString();
    }
}
