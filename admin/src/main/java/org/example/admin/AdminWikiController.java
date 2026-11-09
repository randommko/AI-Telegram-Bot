package org.example.admin;

import org.example.core.aichat.PromptDefaults;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Страница «Вики»: описание механик бота простым языком. Только чтение —
 * ничего не сохраняет и не меняет поведение бота.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminWikiController {

    @GetMapping("/admin/wiki")
    String page(Model model) {
        model.addAttribute("title", "Вики");
        model.addAttribute("technical", PromptDefaults.TECHNICAL);
        model.addAttribute("answerFormat", PromptDefaults.ANSWER_FORMAT);
        model.addAttribute("emotionsHeader", PromptDefaults.EMOTIONS_HEADER);
        model.addAttribute("grudgesHeader", PromptDefaults.GRUDGES_HEADER);
        model.addAttribute("memoryCoreHeader", PromptDefaults.MEMORY_CORE_HEADER);
        model.addAttribute("memoryRegularHeader", PromptDefaults.MEMORY_REGULAR_HEADER);
        model.addAttribute("peopleHeader", PromptDefaults.PEOPLE_HEADER);
        model.addAttribute("timeTemplate", PromptDefaults.TIME_TEMPLATE);
        model.addAttribute("timeZone", PromptDefaults.TIME_ZONE);
        model.addAttribute("personaFallback", PromptDefaults.PERSONA_FALLBACK);
        model.addAttribute("reactionPrompt", PromptDefaults.REACTION_PROMPT);
        return "admin/wiki";
    }
}
