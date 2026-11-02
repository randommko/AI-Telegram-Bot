package org.example.Admin;

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
import static org.example.Settings.Settings.AI_CONTEXT_FOR_MY_CHAT;
import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.example.Settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.Settings.Settings.AI_MODEL_KEY;
import static org.example.Settings.Settings.AI_PERSONA_JOIN_TEMPLATE;
import static org.example.Settings.Settings.AI_PERSONA_LEAVE_TEMPLATE;
import static org.example.Settings.Settings.OWNER_CHAT_ID;

/** Параметры модели, лимитов, промптов и шаблонов. Всё читается ботом на каждый запрос. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminSettingsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminSettingsController.class);
    private static final String KIND_TEXT = "text";
    private static final String KIND_AREA = "textarea";

    /** Описание поля формы: ключ настройки, подпись и вид ввода. */
    record Field(String key, String title, String kind) {
    }

    /** Поле с текущим значением и ошибкой для вывода. */
    public record FieldView(String key, String title, String kind, String value, String error) {
    }

    public record GroupView(String title, List<FieldView> fields) {
    }

    private static final List<GroupView> FORM = List.of(
            new GroupView("Модель", List.of(
                    field(AI_MODEL_KEY, "Модель", KIND_TEXT),
                    field(AI_ANSWER_TEMPERATURE, "Температура ответа (0..2)", KIND_TEXT),
                    field(AI_MAX_TOKENS_ANSWER_QUESTION, "Лимит токенов ответа (1..8192)", KIND_TEXT),
                    field(AI_HISTORY_SIZE, "Сообщений в истории чата", KIND_TEXT))),
            new GroupView("Память", List.of(
                    field(AI_MEMORY_PROMPT_SIZE, "Обычных воспоминаний в промпте", KIND_TEXT),
                    field(AI_MEMORY_ANALYZE_EVERY, "Порог анализа переписки", KIND_TEXT),
                    field(AI_MEMORY_EXTRACT_PROMPT, "Промпт анализа переписки", KIND_AREA))),
            new GroupView("Промпты", List.of(
                    field(AI_CONTEXT, "Промпт чата без персоны", KIND_AREA),
                    field(AI_CONTEXT_FOR_MY_CHAT, "Промпт «синего» чата без персоны", KIND_AREA))),
            new GroupView("Шаблоны", List.of(
                    field(AI_PERSONA_JOIN_TEMPLATE, "Вход персоны в чат", KIND_TEXT),
                    field(AI_PERSONA_LEAVE_TEMPLATE, "Выход персоны из чата", KIND_TEXT))),
            new GroupView("Уведомления", List.of(
                    field(OWNER_CHAT_ID, "Telegram ID получателя сообщения о запуске", KIND_TEXT))));

    private final SettingsService settings;
    private final AdminSettingsValidator validator;

    public AdminSettingsController(SettingsService settings, AdminSettingsValidator validator) {
        this.settings = settings;
        this.validator = validator;
    }

    @GetMapping("/admin/settings")
    String page(Model model) {
        model.addAttribute("title", "Настройки");
        model.addAttribute("groups", render(storedValues(), Map.of()));
        model.addAttribute("saved", false);
        return "admin/settings";
    }

    @PostMapping("/admin/settings")
    String save(@RequestParam Map<String, String> params, Model model) {
        Map<String, String> values = submitted(params);
        Map<String, String> errors = validator.validate(values);

        if (!errors.isEmpty()) {
            model.addAttribute("title", "Настройки");
            model.addAttribute("groups", render(values, errors));
            model.addAttribute("saved", false);
            return "admin/settings";
        }

        Map<String, String> stored = storedValues();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            settings.save(entry.getKey(), entry.getValue());
            if (!Objects.equals(stored.get(entry.getKey()), entry.getValue()))
                audit(entry.getKey());
        }
        return "redirect:/admin/settings?saved";
    }

    /** Значения приходят только за отрисованные поля: скрытые настройки форма не перезаписывает. */
    private Map<String, String> submitted(Map<String, String> params) {
        Set<String> keys = FORM.stream()
                .flatMap(group -> group.fields().stream())
                .map(FieldView::key)
                .collect(Collectors.toSet());

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : keys)
            values.put(key, params.getOrDefault(key, ""));
        return values;
    }

    private Map<String, String> storedValues() {
        return settings.listAll().stream()
                .collect(Collectors.toMap(SettingsService.Setting::key, SettingsService.Setting::value));
    }

    /** Значения промптов в лог не пишутся: там реальные имена участников чата. */
    private void audit(String key) {
        logger.info("Настройка {} изменена администратором", key);
    }

    private List<GroupView> render(Map<String, String> values, Map<String, String> errors) {
        List<GroupView> groups = new ArrayList<>();
        for (GroupView group : FORM) {
            List<FieldView> fields = new ArrayList<>();
            for (FieldView field : group.fields())
                fields.add(new FieldView(field.key(), field.title(), field.kind(),
                        values.getOrDefault(field.key(), ""), errors.get(field.key())));
            groups.add(new GroupView(group.title(), fields));
        }
        return groups;
    }

    private static FieldView field(String key, String title, String kind) {
        return new FieldView(key, title, kind, "", null);
    }
}
