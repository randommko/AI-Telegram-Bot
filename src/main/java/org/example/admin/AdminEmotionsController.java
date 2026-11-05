package org.example.admin;

import org.example.aichat.Emotion;
import org.example.aichat.EmotionPhrase;
import org.example.aichat.EmotionRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Справочник эмоций по вкладкам: у каждой шкалы свои подпись, вес в агрессию, затухание,
 * базовое значение и пять фраз по полосам значения. Производная агрессия получает
 * вкладку только с фразами — её значение вычисляется из остальных эмоций.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminEmotionsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminEmotionsController.class);

    public record EmotionView(Emotion emotion, String title, String weight, String decay, String base) {
    }

    public record PhraseRow(String fieldKey, int from, int to, String phrase) {
    }

    public record TabView(String code, String title, String description, boolean derived, boolean active,
                          boolean hasErrors, EmotionView fields, List<PhraseRow> phrases) {
    }

    private final EmotionRepository emotions;
    private final AdminEmotionsValidator validator;

    public AdminEmotionsController(EmotionRepository emotions, AdminEmotionsValidator validator) {
        this.emotions = emotions;
        this.validator = validator;
    }

    @GetMapping("/admin/emotions")
    String page(@RequestParam(required = false) String tab,
                @RequestParam(required = false) String saved, Model model) {
        return render(model, Map.of(), Map.of(), saved != null, tab);
    }

    @PostMapping("/admin/emotions")
    String save(@RequestParam Map<String, String> params, Model model, RedirectAttributes redirect) {
        String tab = params.get("tab");
        List<Emotion> catalog = emotions.findCatalog();
        Map<String, List<EmotionPhrase>> phrases = emotions.findPhrases();

        AdminEmotionsValidator.Result result = validator.validate(catalog, phrases, params);
        if (result.hasErrors())
            return render(model, result.errors(), params, false, tab);

        for (AdminEmotionsValidator.EmotionEdit edit : result.emotions())
            emotions.updateEmotion(edit.emotion().code(), edit.title(), edit.weight(), edit.decay(), edit.base());
        for (AdminEmotionsValidator.PhraseEdit edit : result.phrases())
            emotions.updatePhrase(edit.emotionCode(), edit.fromValue(), edit.phrase());

        logger.info("Справочник эмоций изменён администратором");
        redirect.addFlashAttribute("saved", "Справочник эмоций сохранён");
        redirect.addAttribute("tab", activeTab(catalog, Map.of(), tab));
        return "redirect:/admin/emotions";
    }

    private String render(Model model, Map<String, String> errors, Map<String, String> values, boolean saved,
                          String requestedTab) {
        List<Emotion> catalog = emotions.findCatalog();
        Map<String, List<EmotionPhrase>> phrases = emotions.findPhrases();
        String activeTab = activeTab(catalog, errors, requestedTab);

        List<TabView> tabs = new ArrayList<>();
        for (Emotion emotion : catalog) {
            String code = emotion.code();
            tabs.add(new TabView(
                    code,
                    emotion.title(),
                    emotion.description(),
                    emotion.derived(),
                    code.equals(activeTab),
                    hasErrors(errors, code),
                    emotion.derived() ? null : fields(emotion, values),
                    phraseRows(code, phrases.get(code), values)));
        }

        model.addAttribute("title", "Эмоции");
        model.addAttribute("tabs", tabs);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("weightSum", String.format(Locale.ROOT, "%.2f", positiveWeight(catalog)));
        model.addAttribute("aggressionCeiling", aggressionCeiling(catalog));
        model.addAttribute("errors", errors);
        model.addAttribute("saved", saved);
        return "admin/emotions";
    }

    private EmotionView fields(Emotion emotion, Map<String, String> values) {
        String code = emotion.code();
        return new EmotionView(
                emotion,
                value(values, "title_" + code, emotion.title()),
                value(values, "weight_" + code, String.valueOf(emotion.aggressionWeight())),
                value(values, "decay_" + code, String.valueOf(emotion.decayPerHour())),
                value(values, "base_" + code, String.valueOf(emotion.baseDefault())));
    }

    private List<PhraseRow> phraseRows(String code, List<EmotionPhrase> phrases, Map<String, String> values) {
        List<PhraseRow> rows = new ArrayList<>();
        if (phrases == null)
            return rows;

        for (EmotionPhrase phrase : phrases) {
            String key = "phrase_" + code + "_" + phrase.fromValue();
            rows.add(new PhraseRow(key, phrase.fromValue(), phrase.toValue(), value(values, key, phrase.phrase())));
        }
        return rows;
    }

    /** Вкладка ошибки важнее запрошенной: иначе ошибка останется на скрытой вкладке. */
    private String activeTab(List<Emotion> catalog, Map<String, String> errors, String requested) {
        String errorTab = tabOfFirstError(errors, catalog);
        if (errorTab != null)
            return errorTab;

        for (Emotion emotion : catalog) {
            if (emotion.code().equals(requested))
                return emotion.code();
        }
        return catalog.isEmpty() ? "" : catalog.get(0).code();
    }

    private String tabOfFirstError(Map<String, String> errors, List<Emotion> catalog) {
        for (String key : errors.keySet()) {
            for (Emotion emotion : catalog) {
                if (belongsTo(key, emotion.code()))
                    return emotion.code();
            }
        }
        return null;
    }

    private boolean hasErrors(Map<String, String> errors, String code) {
        for (String key : errors.keySet()) {
            if (belongsTo(key, code))
                return true;
        }
        return false;
    }

    /** Ключи ошибок собираются как `<поле>_<код>` и `phrase_<код>_<полоса>`. */
    private boolean belongsTo(String key, String code) {
        return key.endsWith("_" + code) || key.startsWith("phrase_" + code + "_");
    }

    static double positiveWeight(List<Emotion> catalog) {
        double sum = 0;
        for (Emotion emotion : catalog) {
            if (!emotion.derived() && emotion.aggressionWeight() > 0)
                sum += emotion.aggressionWeight();
        }
        return sum;
    }

    /** Потолок агрессии считается как в EmotionService.aggression(): округление вниз и не больше 10. */
    static int aggressionCeiling(List<Emotion> catalog) {
        return (int) Math.floor(Math.min(10, positiveWeight(catalog) * 10) + 1e-9);
    }

    private String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }
}
