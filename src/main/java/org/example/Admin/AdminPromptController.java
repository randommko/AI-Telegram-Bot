package org.example.Admin;

import org.example.AiChat.PersonaRepository;
import org.example.AiChat.PromptComposer;
import org.example.AiChat.PromptSection;
import org.example.Settings.SettingsService;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Страница «Промпт»: разделы в порядке сборки, их шаблоны, бюджеты, сброс
 * по умолчанию и предпросмотр собранного промпта на реальном чате.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminPromptController {
    private static final Logger logger = LoggerFactory.getLogger(AdminPromptController.class);
    private static final String DEFAULT_QUESTION = "как дела?";

    public record TemplateView(String key, String label, String value, String defaultValue,
                               int length, int budget, boolean multiline) {
    }

    public record SectionView(PromptSection spec, List<TemplateView> templates) {
    }

    public record ChatView(Long chatId, String title, boolean hasPersona) {
    }

    private final PromptComposer composer;
    private final SettingsService settings;
    private final PersonaRepository personas;

    public AdminPromptController(PromptComposer composer, SettingsService settings, PersonaRepository personas) {
        this.composer = composer;
        this.settings = settings;
        this.personas = personas;
    }

    @GetMapping("/admin/prompt")
    String page(@RequestParam(required = false) String saved, Model model) {
        return render(model, saved != null);
    }

    @PostMapping("/admin/prompt")
    String save(@RequestParam Map<String, String> params, RedirectAttributes redirect) {
        Map<String, String> stored = storedValues();
        String reset = params.get("reset");

        for (PromptSection section : PromptSection.values()) {
            for (PromptSection.Template template : section.templates()) {
                if (template.key().equals(reset)) {
                    settings.save(template.key(), template.defaultText());
                    logger.info("Шаблон {} возвращён по умолчанию", template.key());
                    continue;
                }
                if (!params.containsKey(template.key()))
                    continue;

                String value = params.get(template.key());
                if (!value.equals(stored.getOrDefault(template.key(), ""))) {
                    settings.save(template.key(), value);
                    logger.info("Шаблон {} изменён администратором", template.key());
                }
            }
        }

        redirect.addFlashAttribute("saved", "Шаблоны промпта сохранены");
        return "redirect:/admin/prompt";
    }

    /** Предпросмотр читает реальные данные чата и ничего не пишет: эмоции читаются без добивки строк. */
    @GetMapping("/admin/prompt/preview")
    String preview(@RequestParam(required = false) Long chatId,
                   @RequestParam(required = false) String question,
                   Model model) {
        List<ChatView> chats = chats();
        Long selected = chatId != null ? chatId
                : chats.stream().filter(ChatView::hasPersona).map(ChatView::chatId).findFirst()
                .orElse(chats.isEmpty() ? null : chats.get(0).chatId());

        model.addAttribute("title", "Предпросмотр промпта");
        model.addAttribute("chats", chats);
        model.addAttribute("chatId", selected);
        model.addAttribute("question", question == null || question.isBlank() ? DEFAULT_QUESTION : question);
        model.addAttribute("prompt", selected == null ? "" : composer.compose(selected, true).text());
        return "admin/prompt-preview";
    }

    private String render(Model model, boolean saved) {
        Map<String, String> stored = storedValues();
        List<SectionView> sections = new ArrayList<>();

        for (PromptSection section : PromptSection.values()) {
            List<TemplateView> templates = section.templates().stream().map(template -> {
                String value = stored.getOrDefault(template.key(), "");
                if (value.isBlank())
                    value = template.defaultText();
                return new TemplateView(template.key(), template.label(), value, template.defaultText(),
                        value.length(), section.budget(), template.multiline());
            }).toList();
            sections.add(new SectionView(section, templates));
        }

        model.addAttribute("title", "Промпт");
        model.addAttribute("sections", sections);
        model.addAttribute("chats", chats());
        model.addAttribute("saved", saved);
        return "admin/prompt";
    }

    private Map<String, String> storedValues() {
        return settings.listAll().stream()
                .collect(Collectors.toMap(SettingsService.Setting::key, SettingsService.Setting::value));
    }

    private List<ChatView> chats() {
        return personas.findAllChats().stream()
                .map(chat -> new ChatView(chat.chatId(),
                        chat.title() == null || chat.title().isBlank() ? String.valueOf(chat.chatId()) : chat.title(),
                        chat.personaId() != null))
                .toList();
    }
}
