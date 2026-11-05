package org.example.aichat;

import java.util.List;
import java.util.Map;

/** План ответа: что спросили, что приложить, как сдвинуть настроение и сколько реплик отправить. */
public record AnswerPlan(
        String topic,
        PlanIntent intent,
        List<Integer> memoryIds,
        Map<String, Integer> emotionDeltas,
        String emotionReason,
        int replies,
        List<String> targets) {
}
