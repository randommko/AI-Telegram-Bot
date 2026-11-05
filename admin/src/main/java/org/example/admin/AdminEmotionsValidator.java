package org.example.admin;

import org.example.core.aichat.Emotion;
import org.example.core.aichat.EmotionPhrase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Проверка формы справочника эмоций: диапазоны шкал и непустые тексты. */
@Component
public class AdminEmotionsValidator {

    public record EmotionEdit(Emotion emotion, String title, double weight, double decay, int base) {
    }

    public record PhraseEdit(String emotionCode, int fromValue, String phrase) {
    }

    public record Result(Map<String, String> errors, List<EmotionEdit> emotions, List<PhraseEdit> phrases) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public Result validate(List<Emotion> catalog, Map<String, List<EmotionPhrase>> phrases,
                           Map<String, String> params) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<EmotionEdit> emotions = new ArrayList<>();

        for (Emotion emotion : catalog) {
            if (emotion.derived())
                continue;

            String code = emotion.code();
            String titleKey = "title_" + code;
            String title = params.getOrDefault(titleKey, emotion.title()).trim();
            if (title.isEmpty())
                errors.put(titleKey, "Подпись не может быть пустой");

            double weight = decimal(params.get("weight_" + code), -1, 1, "weight_" + code, errors,
                    emotion.aggressionWeight());
            double decay = decimal(params.get("decay_" + code), 0, 100, "decay_" + code, errors,
                    emotion.decayPerHour());
            int base = whole(params.get("base_" + code), 0, 10, "base_" + code, errors, emotion.baseDefault());

            emotions.add(new EmotionEdit(emotion, title, weight, decay, base));
        }

        List<PhraseEdit> phraseEdits = new ArrayList<>();
        for (Map.Entry<String, List<EmotionPhrase>> entry : phrases.entrySet()) {
            for (EmotionPhrase phrase : entry.getValue()) {
                String key = "phrase_" + entry.getKey() + "_" + phrase.fromValue();
                String value = params.getOrDefault(key, phrase.phrase()).trim();
                if (value.isEmpty())
                    errors.put(key, "Фраза не может быть пустой");
                phraseEdits.add(new PhraseEdit(entry.getKey(), phrase.fromValue(), value));
            }
        }

        return new Result(errors, emotions, phraseEdits);
    }

    private double decimal(String raw, double min, double max, String key,
                           Map<String, String> errors, double fallback) {
        if (raw == null || raw.isBlank()) {
            errors.put(key, "Значение не может быть пустым");
            return fallback;
        }

        try {
            double value = Double.parseDouble(raw.trim().replace(',', '.'));
            if (value < min || value > max) {
                errors.put(key, "Допустимо от " + min + " до " + max);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.put(key, "Нужно число");
            return fallback;
        }
    }

    private int whole(String raw, int min, int max, String key, Map<String, String> errors, int fallback) {
        if (raw == null || raw.isBlank()) {
            errors.put(key, "Значение не может быть пустым");
            return fallback;
        }

        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                errors.put(key, "Допустимо от " + min + " до " + max);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.put(key, "Нужно целое число");
            return fallback;
        }
    }
}
