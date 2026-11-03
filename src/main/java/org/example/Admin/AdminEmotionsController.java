package org.example.Admin;

import org.example.AiChat.Emotion;
import org.example.AiChat.EmotionPhrase;
import org.example.AiChat.EmotionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Справочник эмоций: подпись, вес в агрессию, скорость затухания и базовое значение,
 * плюс текст фразы для каждой полосы значения. Производная агрессия не редактируется.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminEmotionsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminEmotionsController.class);

    public record EmotionView(Emotion emotion, String title, String weight, String decay, String base) {
    }

    public record PhraseRow(String fieldKey, String title, int from, int to, String phrase) {
    }

    private final EmotionRepository emotions;
    private final AdminEmotionsValidator validator;

    public AdminEmotionsController(EmotionRepository emotions, AdminEmotionsValidator validator) {
        this.emotions = emotions;
        this.validator = validator;
    }

    @GetMapping("/admin/emotions")
    String page(@RequestParam(required = false) String saved, Model model) {
        return render(model, Map.of(), Map.of(), saved != null);
    }

    @PostMapping("/admin/emotions")
    String save(@RequestParam Map<String, String> params, Model model, RedirectAttributes redirect) {
        List<Emotion> catalog = emotions.findCatalog();
        Map<String, List<EmotionPhrase>> phrases = emotions.findPhrases();

        AdminEmotionsValidator.Result result = validator.validate(catalog, phrases, params);
        if (result.hasErrors())
            return render(model, result.errors(), params, false);

        for (AdminEmotionsValidator.EmotionEdit edit : result.emotions())
            emotions.updateEmotion(edit.emotion().code(), edit.title(), edit.weight(), edit.decay(), edit.base());
        for (AdminEmotionsValidator.PhraseEdit edit : result.phrases())
            emotions.updatePhrase(edit.emotionCode(), edit.fromValue(), edit.phrase());

        logger.info("Справочник эмоций изменён администратором");
        redirect.addFlashAttribute("saved", "Справочник эмоций сохранён");
        return "redirect:/admin/emotions";
    }

    private String render(Model model, Map<String, String> errors, Map<String, String> values, boolean saved) {
        List<Emotion> catalog = emotions.findCatalog();
        Map<String, String> titles = new LinkedHashMap<>();
        for (Emotion emotion : catalog)
            titles.put(emotion.code(), emotion.title());

        List<EmotionView> views = new ArrayList<>();
        for (Emotion emotion : catalog) {
            if (emotion.derived())
                continue;
            String code = emotion.code();
            views.add(new EmotionView(
                    emotion,
                    value(values, "title_" + code, emotion.title()),
                    value(values, "weight_" + code, String.valueOf(emotion.aggressionWeight())),
                    value(values, "decay_" + code, String.valueOf(emotion.decayPerHour())),
                    value(values, "base_" + code, String.valueOf(emotion.baseDefault()))));
        }

        List<PhraseRow> phrases = new ArrayList<>();
        for (Map.Entry<String, List<EmotionPhrase>> entry : emotions.findPhrases().entrySet()) {
            for (EmotionPhrase phrase : entry.getValue()) {
                String key = "phrase_" + entry.getKey() + "_" + phrase.fromValue();
                phrases.add(new PhraseRow(key, titles.getOrDefault(entry.getKey(), entry.getKey()),
                        phrase.fromValue(), phrase.toValue(), value(values, key, phrase.phrase())));
            }
        }

        model.addAttribute("title", "Эмоции");
        model.addAttribute("emotions", views);
        model.addAttribute("derived", catalog.stream().filter(Emotion::derived).toList());
        model.addAttribute("phrases", phrases);
        model.addAttribute("errors", errors);
        model.addAttribute("saved", saved);
        return "admin/emotions";
    }

    private String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }
}
