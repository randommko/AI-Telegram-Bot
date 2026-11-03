package org.example.Admin;

import org.example.Settings.Settings;
import org.example.Settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_CONTEXT;
import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.example.Settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.Settings.Settings.AI_MODEL_KEY;
import static org.example.Settings.Settings.AI_PERSONA_JOIN_TEMPLATE;
import static org.example.Settings.Settings.AI_PERSONA_LEAVE_TEMPLATE;
import static org.example.Settings.Settings.OWNER_CHAT_ID;

/**
 * Настройки на пяти вкладках. Вкладка — параметр {@code ?tab=}: каждая открывается
 * отдельным запросом, поэтому можно вернуться на ту же после сохранения, а форма
 * отправляет только поля своей вкладки.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminSettingsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminSettingsController.class);
    private static final String DEFAULT_TAB = "model";

    /** Поле формы: ключ настройки, подпись, вид ввода и границы для ползунка. */
    record Field(String key, String title, String kind, String min, String max, String step, List<String> options) {
    }

    /** Поле с текущим значением, описанием из базы и ошибкой. */
    public record FieldView(String key, String title, String kind, String value, String error, String note,
                            List<String> options, String min, String max, String step) {
    }

    public record TabView(String id, String title, boolean active, List<FieldView> fields) {
    }

    private static final List<TabView> TABS = List.of(
            new TabView("model", "Модель", false, List.of(
                    select(AI_MODEL_KEY, "Модель", Settings.AVAILABLE_AI_MODELS),
                    range(AI_ANSWER_TEMPERATURE, "Температура ответа", "0", "2", "0.1"),
                    range(AI_MAX_TOKENS_ANSWER_QUESTION, "Лимит токенов ответа", "1", "8192", "64"),
                    range(AI_HISTORY_SIZE, "Сообщений в истории чата", "1", "1000", "10"))),
            new TabView("memory", "Память", false, List.of(
                    range(AI_MEMORY_PROMPT_SIZE, "Обычных воспоминаний в промпте", "0", "100", "1"),
                    range(AI_MEMORY_ANALYZE_EVERY, "Порог анализа переписки", "1", "1000", "1"),
                    area(AI_MEMORY_EXTRACT_PROMPT, "Промпт анализа переписки"))),
            new TabView("prompts", "Промты", false, List.of(
                    area(AI_CONTEXT, "Промпт чата без персоны"))),
            new TabView("templates", "Шаблоны", false, List.of(
                    text(AI_PERSONA_JOIN_TEMPLATE, "Вход персоны в чат"),
                    text(AI_PERSONA_LEAVE_TEMPLATE, "Выход персоны из чата"))),
            new TabView("notifications", "Уведомления", false, List.of(
                    text(OWNER_CHAT_ID, "Telegram ID получателя сообщения о запуске"))));

    private final SettingsService settings;
    private final AdminSettingsValidator validator;

    public AdminSettingsController(SettingsService settings, AdminSettingsValidator validator) {
        this.settings = settings;
        this.validator = validator;
    }

    @GetMapping("/admin/settings")
    String page(@RequestParam(required = false) String tab,
                @RequestParam(required = false) String saved,
                Model model) {
        return render(model, storedValues(), Map.of(), tabId(tab), saved != null);
    }

    @PostMapping("/admin/settings")
    String save(@RequestParam Map<String, String> params, Model model) {
        String tab = tabId(params.get("tab"));
        Map<String, String> values = submitted(params, tab);
        Map<String, String> errors = validator.validate(values);

        if (!errors.isEmpty())
            return render(model, storedValues(), errors, tab, false, values);

        Map<String, String> stored = storedValues();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            settings.save(entry.getKey(), entry.getValue());
            if (!Objects.equals(stored.get(entry.getKey()), entry.getValue()))
                audit(entry.getKey());
        }
        return "redirect:/admin/settings?tab=" + tab + "&saved";
    }

    /** Значения приходят только за поля открытой вкладки: остальные настройки форма не перезаписывает. */
    private Map<String, String> submitted(Map<String, String> params, String tab) {
        Set<String> keys = fields(tab).stream().map(FieldView::key).collect(Collectors.toSet());

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : keys)
            values.put(key, params.getOrDefault(key, ""));
        return values;
    }

    private List<FieldView> fields(String tab) {
        for (TabView tabView : TABS) {
            if (tabView.id().equals(tab))
                return tabView.fields();
        }
        return TABS.get(0).fields();
    }

    private Map<String, String> storedValues() {
        return settings.listAll().stream()
                .collect(Collectors.toMap(SettingsService.Setting::key, SettingsService.Setting::value));
    }

    /** Значения промптов в лог не пишутся: там реальные имена участников чата. */
    private void audit(String key) {
        logger.info("Настройка {} изменена администратором", key);
    }

    private String render(Model model, Map<String, String> stored, Map<String, String> errors, String tab,
                          boolean saved) {
        return render(model, stored, errors, tab, saved, stored);
    }

    private String render(Model model, Map<String, String> stored, Map<String, String> errors, String tab,
                          boolean saved, Map<String, String> values) {
        Map<String, String> notes = settings.listAll().stream()
                .filter(setting -> setting.description() != null)
                .collect(Collectors.toMap(SettingsService.Setting::key, SettingsService.Setting::description));

        List<TabView> tabs = new ArrayList<>();
        for (TabView tabView : TABS) {
            List<FieldView> fields = new ArrayList<>();
            for (FieldView field : tabView.fields()) {
                String value = values.getOrDefault(field.key(), stored.getOrDefault(field.key(), ""));
                fields.add(new FieldView(field.key(), field.title(), field.kind(), value,
                        errors.get(field.key()), notes.getOrDefault(field.key(), ""),
                        field.options(), field.min(), field.max(), field.step()));
            }
            tabs.add(new TabView(tabView.id(), tabView.title(), tabView.id().equals(tab), fields));
        }

        model.addAttribute("title", "Настройки");
        model.addAttribute("tabs", tabs);
        model.addAttribute("activeTab", tab);
        model.addAttribute("saved", saved);
        return "admin/settings";
    }

    private static String tabId(String tab) {
        for (TabView tabView : TABS) {
            if (tabView.id().equals(tab))
                return tab;
        }
        return DEFAULT_TAB;
    }

    private static FieldView text(String key, String title) {
        return new FieldView(key, title, "text", "", null, "", List.of(), null, null, null);
    }

    private static FieldView area(String key, String title) {
        return new FieldView(key, title, "textarea", "", null, "", List.of(), null, null, null);
    }

    private static FieldView select(String key, String title, List<String> options) {
        return new FieldView(key, title, "select", "", null, "", options, null, null, null);
    }

    private static FieldView range(String key, String title, String min, String max, String step) {
        return new FieldView(key, title, "range", "", null, "", List.of(), min, max, step);
    }
}
