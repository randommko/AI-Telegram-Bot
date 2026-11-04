package org.example.Admin;

import org.example.AiChat.LlmCatalog;
import org.example.Config.BotProperties;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.Settings.Settings.AI_ANSWER_TEMPERATURE;
import static org.example.Settings.Settings.AI_CONTEXT;
import static org.example.Settings.Settings.AI_GRUDGE_ANGER_DELTA;
import static org.example.Settings.Settings.AI_GRUDGE_DECAY_PER_HOUR;
import static org.example.Settings.Settings.AI_GRUDGE_INSULT_STRENGTH;
import static org.example.Settings.Settings.AI_GRUDGE_THRESHOLD;
import static org.example.Settings.Settings.AI_HISTORY_SIZE;
import static org.example.Settings.Settings.AI_MAX_TOKENS_ANSWER_QUESTION;
import static org.example.Settings.Settings.AI_MEMORY_ANALYSIS_MAX_TOKENS;
import static org.example.Settings.Settings.AI_MEMORY_ANALYSIS_MESSAGES;
import static org.example.Settings.Settings.AI_MEMORY_ANALYZE_EVERY;
import static org.example.Settings.Settings.AI_MEMORY_EXTRACT_PROMPT;
import static org.example.Settings.Settings.AI_MEMORY_PROMPT_SIZE;
import static org.example.Settings.Settings.AI_MODEL_KEY;
import static org.example.Settings.Settings.AI_PERSONA_JOIN_TEMPLATE;
import static org.example.Settings.Settings.AI_PERSONA_LEAVE_TEMPLATE;
import static org.example.Settings.Settings.AI_PLAN_MAX_TOKENS;
import static org.example.Settings.Settings.AI_ROUTE_KEY;
import static org.example.Settings.Settings.DEFAULT_AI_MODEL;
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

    /** Кабинеты, где выдают ключи: ссылки живут в админке, чтобы не тяжелеть каталог. */
    private static final Map<String, String> KEY_URLS = Map.ofEntries(
            Map.entry("deepseek", "https://platform.deepseek.com/api_keys"),
            Map.entry("grok", "https://console.x.ai"),
            Map.entry("openai", "https://platform.openai.com/api-keys"),
            Map.entry("gemini", "https://aistudio.google.com/apikey"),
            Map.entry("mistral", "https://console.mistral.ai/api-keys"),
            Map.entry("aitunnel", "https://aitunnel.ru/"),
            Map.entry("venice", "https://venice.ai/settings/api/keys"),
            Map.entry("abliteration", "https://abliteration.ai/console"));

    /** Опция списка моделей: значение — идентификатор модели, title — описание, enabled — есть ли ключ. */
    public record Option(String value, String title, boolean enabled) {

        public Option(String value, String title) {
            this(value, title, true);
        }
    }

    /** Опция каскада: значение, подпись, дверь (провайдер|вендор) и подсказка. */
    public record Choice(String value, String label, String door, String title, boolean enabled) {

        public Choice(String value, String label, String door, String title) {
            this(value, label, door, title, true);
        }
    }

    /** Группа опций — поставщик; hasKey=false добавляет к заголовку имя недостающей переменной. */
    public record OptionGroup(String title, boolean hasKey, String envName, List<Option> options) {
    }

    /** Поле с текущим значением, описанием из базы и ошибкой. */
    public record FieldView(String key, String title, String kind, String value, String error, String note,
                            List<OptionGroup> options, List<Choice> choices, String min, String max, String step) {
    }

    public record TabView(String id, String title, boolean active, List<FieldView> fields) {
    }

    /** Строка инструкции: переменная окружения, поставщик, ссылка на кабинет, что открывает и есть ли ключ. */
    public record KeyHint(String envName, String provider, String keyUrl, String scope, boolean hasKey) {
    }

    /** Выбранная пара, разложенная для формы: режим, провайдер, вендор и модель. */
    private record Selection(String mode, String providerId, String groupId, String modelId) {
        String directModel() {
            return AdminModelForm.DIRECT.equals(mode) ? modelId : "";
        }
    }

    private final SettingsService settings;
    private final AdminSettingsValidator validator;
    private final BotProperties properties;

    public AdminSettingsController(SettingsService settings, AdminSettingsValidator validator,
                                   BotProperties properties) {
        this.settings = settings;
        this.validator = validator;
        this.properties = properties;
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
        Map<String, String> stored = storedValues();
        Map<String, String> errors = validator.validate(values, stored);

        if (!errors.isEmpty())
            return render(model, stored, errors, tab, false, values);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (AdminModelForm.KEYS.contains(entry.getKey()))
                continue;

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
        composeModel(values);
        return values;
    }

    /**
     * Пара «дверь и модель» собирается из режима формы: прямой список даёт модель,
     * каскад — провайдера, вендора и модель. Служебные поля в настройки не попадают.
     */
    private void composeModel(Map<String, String> values) {
        if (!values.containsKey(AI_MODEL_KEY))
            return;

        if (AdminModelForm.PROVIDER.equals(values.getOrDefault(AdminModelForm.ACCESS_KEY, ""))) {
            values.put(AI_MODEL_KEY, values.getOrDefault(AdminModelForm.DOOR_MODEL_KEY, ""));
            values.put(AI_ROUTE_KEY, values.getOrDefault(AdminModelForm.PROVIDER_KEY, ""));
            return;
        }

        values.put(AI_ROUTE_KEY, LlmCatalog.model(values.get(AI_MODEL_KEY))
                .flatMap(LlmCatalog::providerOf)
                .map(LlmCatalog.Provider::id)
                .orElse(""));
    }

    private List<FieldView> fields(String tab) {
        for (TabView tabView : tabs()) {
            if (tabView.id().equals(tab))
                return tabView.fields();
        }
        return tabs().get(0).fields();
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
        for (TabView tabView : tabs()) {
            List<FieldView> fields = new ArrayList<>();
            for (FieldView field : tabView.fields()) {
                String value = values.getOrDefault(field.key(), stored.getOrDefault(field.key(), field.value()));
                if (AdminModelForm.KEYS.contains(field.key()) && value.isEmpty())
                    value = field.value();

                fields.add(new FieldView(field.key(), field.title(), field.kind(), value,
                        errors.get(field.key()), notes.getOrDefault(field.key(), ""),
                        field.options(), field.choices(), field.min(), field.max(), field.step()));
            }
            tabs.add(new TabView(tabView.id(), tabView.title(), tabView.id().equals(tab), fields));
        }

        model.addAttribute("title", "Настройки");
        model.addAttribute("tabs", tabs);
        model.addAttribute("activeTab", tab);
        model.addAttribute("saved", saved);
        model.addAttribute("keyHints", keyHints());
        return "admin/settings";
    }

    private String tabId(String tab) {
        for (TabView tabView : tabs()) {
            if (tabView.id().equals(tab))
                return tab;
        }
        return DEFAULT_TAB;
    }

    /** Вкладки собираются на каждый запрос: у групп моделей признак ключа меняется вместе с окружением. */
    private List<TabView> tabs() {
        Selection selection = selection();

        return List.of(
                new TabView("model", "Модель", false, List.of(
                        radio(AdminModelForm.ACCESS_KEY, "Способ выбора модели", selection.mode()),
                        hidden(AI_ROUTE_KEY),
                        select(AI_MODEL_KEY, "Модель", modelGroups(selection), selection.directModel()),
                        cascade("provider", AdminModelForm.PROVIDER_KEY, "Провайдер",
                                providerChoices(selection), selection.providerId()),
                        cascade("group", AdminModelForm.GROUP_KEY, "Группа моделей",
                                groupChoices(), selection.groupId()),
                        cascade("door-model", AdminModelForm.DOOR_MODEL_KEY, "Модель у провайдера",
                                doorModelChoices(selection), selection.modelId()),
                        range(AI_ANSWER_TEMPERATURE, "Температура ответа", "0", "2", "0.1"),
                        range(AI_MAX_TOKENS_ANSWER_QUESTION, "Лимит токенов ответа", "1", "8192", "64"),
                        range(AI_PLAN_MAX_TOKENS, "Лимит токенов плана", "1", "8192", "64"),
                        range(AI_MEMORY_ANALYSIS_MAX_TOKENS, "Лимит токенов анализа памяти", "1", "8192", "64"),
                        range(AI_MEMORY_ANALYSIS_MESSAGES, "Сообщений в анализе памяти", "1", "1000", "10"),
                        range(AI_HISTORY_SIZE, "Сообщений в истории чата", "1", "1000", "10"))),
                new TabView("memory", "Память", false, List.of(
                        range(AI_MEMORY_PROMPT_SIZE, "Обычных воспоминаний в промпте", "0", "100", "1"),
                        range(AI_MEMORY_ANALYZE_EVERY, "Порог анализа переписки", "1", "1000", "1"),
                        area(AI_MEMORY_EXTRACT_PROMPT, "Промпт анализа переписки"),
                        range(AI_GRUDGE_INSULT_STRENGTH, "Обида за оскорбление", "0", "10", "1"),
                        range(AI_GRUDGE_ANGER_DELTA, "Гнев за оскорбление", "0", "10", "1"),
                        range(AI_GRUDGE_DECAY_PER_HOUR, "Затухание обиды в час", "0", "10", "1"),
                        range(AI_GRUDGE_THRESHOLD, "Порог адресата колкостей", "0", "10", "1"))),
                new TabView("prompts", "Промты", false, List.of(
                        area(AI_CONTEXT, "Промпт чата без персоны"))),
                new TabView("templates", "Шаблоны", false, List.of(
                        text(AI_PERSONA_JOIN_TEMPLATE, "Вход персоны в чат"),
                        text(AI_PERSONA_LEAVE_TEMPLATE, "Выход персоны из чата"))),
                new TabView("notifications", "Уведомления", false, List.of(
                        text(OWNER_CHAT_ID, "Telegram ID получателя сообщения о запуске"))));
    }

    /** Выбранная пара из базы: режим и три ступени каскада для формы. */
    private Selection selection() {
        Map<String, String> stored = storedValues();
        String route = stored.getOrDefault(AI_ROUTE_KEY, "").trim();
        String configured = stored.getOrDefault(AI_MODEL_KEY, "").trim();
        String modelId = configured.isEmpty() ? DEFAULT_AI_MODEL : configured;

        Optional<LlmCatalog.Model> found = LlmCatalog.model(route, modelId);
        if (found.isEmpty())
            return new Selection(AdminModelForm.DIRECT, "", "", modelId);

        LlmCatalog.Model model = found.get();
        boolean aggregator = LlmCatalog.provider(model.providerId())
                .map(LlmCatalog.Provider::aggregator)
                .orElse(false);
        return new Selection(aggregator ? AdminModelForm.PROVIDER : AdminModelForm.DIRECT,
                model.providerId(), model.group(), model.id());
    }

    /** Инструкция по ключам: переменная, поставщик и что эта дверь открывает. */
    private List<KeyHint> keyHints() {
        List<KeyHint> hints = new ArrayList<>();
        for (LlmCatalog.Provider provider : LlmCatalog.providers()) {
            String scope;
            if (provider.aggregator()) {
                String groups = LlmCatalog.groupsOf(provider.id()).stream()
                        .map(LlmCatalog.Group::title)
                        .collect(Collectors.joining(", "));
                scope = "группы: " + groups + " · моделей: " + LlmCatalog.modelsOf(provider.id()).size();
            } else {
                scope = "модели: " + LlmCatalog.modelsOf(provider.id()).stream()
                        .map(LlmCatalog.Model::id)
                        .collect(Collectors.joining(", "));
            }

            hints.add(new KeyHint(provider.envName(), provider.title(),
                    KEY_URLS.getOrDefault(provider.id(), ""), scope,
                    properties.ai().keyFor(provider.id()) != null));
        }
        return hints;
    }

    /** Модели прямых поставщиков, сгруппированные по поставщику; у группы без ключа — пометка. */
    private List<OptionGroup> modelGroups(Selection selection) {
        List<OptionGroup> groups = new ArrayList<>();
        for (LlmCatalog.Provider provider : LlmCatalog.directProviders()) {
            boolean hasKey = properties.ai().keyFor(provider.id()) != null;
            List<Option> options = LlmCatalog.modelsOf(provider.id()).stream()
                    .map(model -> new Option(model.id(), model.title(),
                            hasKey || isCurrent(selection, provider.id(), model.id())))
                    .toList();
            groups.add(new OptionGroup(provider.title(), hasKey, provider.envName(), options));
        }
        return groups;
    }

    /** Провайдеры-агрегаторы; отсутствие ключа видно в самом пункте списка, а не только в подсказке. */
    private List<Choice> providerChoices(Selection selection) {
        List<Choice> choices = new ArrayList<>();
        for (LlmCatalog.Provider provider : LlmCatalog.aggregators()) {
            boolean hasKey = properties.ai().keyFor(provider.id()) != null;
            boolean enabled = hasKey || isCurrent(selection, provider.id(), selection.modelId());
            choices.add(new Choice(provider.id(),
                    hasKey ? provider.title() : provider.title() + " — нет ключа",
                    "",
                    hasKey ? provider.title() : provider.title() + " — нет ключа " + provider.envName(),
                    enabled));
        }
        return choices;
    }

    /** Пары «агрегатор и вендор»: второй список каскада. */
    private List<Choice> groupChoices() {
        List<Choice> choices = new ArrayList<>();
        for (LlmCatalog.Provider provider : LlmCatalog.aggregators())
            for (LlmCatalog.Group group : LlmCatalog.groupsOf(provider.id()))
                choices.add(new Choice(group.id(), group.title(), door(provider.id(), group.id()), group.title()));
        return choices;
    }

    /** Модели всех агрегаторов: третий список, отфильтрованный по провайдеру и вендору. */
    private List<Choice> doorModelChoices(Selection selection) {
        List<Choice> choices = new ArrayList<>();
        for (LlmCatalog.Provider provider : LlmCatalog.aggregators()) {
            boolean hasKey = properties.ai().keyFor(provider.id()) != null;
            for (LlmCatalog.Model model : LlmCatalog.modelsOf(provider.id()))
                choices.add(new Choice(model.id(), model.id(), door(provider.id(), model.group()),
                        model.title(), hasKey || isCurrent(selection, provider.id(), model.id())));
        }
        return choices;
    }

    /** Сохранённая пара остаётся выбираемой: иначе снятый ключ молча переключил бы модель при сохранении. */
    private static boolean isCurrent(Selection selection, String providerId, String modelId) {
        return providerId.equals(selection.providerId()) && modelId.equals(selection.modelId());
    }

    private static String door(String providerId, String groupId) {
        return providerId + "|" + groupId;
    }

    private static FieldView radio(String key, String title, String value) {
        List<Choice> choices = List.of(
                new Choice(AdminModelForm.DIRECT, "Прямой доступ", "", "Модели напрямую у поставщика"),
                new Choice(AdminModelForm.PROVIDER, "Провайдеры", "", "Модели через агрегатор"));
        return new FieldView(key, title, "radio", value, null, "", List.of(), choices, null, null, null);
    }

    private static FieldView hidden(String key) {
        return new FieldView(key, "", "hidden", "", null, "", List.of(), List.of(), null, null, null);
    }

    private static FieldView cascade(String kind, String key, String title, List<Choice> choices, String value) {
        return new FieldView(key, title, kind, value, null, "", List.of(), choices, null, null, null);
    }

    private static FieldView text(String key, String title) {
        return new FieldView(key, title, "text", "", null, "", List.of(), List.of(), null, null, null);
    }

    private static FieldView area(String key, String title) {
        return new FieldView(key, title, "textarea", "", null, "", List.of(), List.of(), null, null, null);
    }

    private static FieldView select(String key, String title, List<OptionGroup> options, String value) {
        return new FieldView(key, title, "select", value, null, "", options, List.of(), null, null, null);
    }

    private static FieldView range(String key, String title, String min, String max, String step) {
        return new FieldView(key, title, "range", "", null, "", List.of(), List.of(), min, max, step);
    }
}
