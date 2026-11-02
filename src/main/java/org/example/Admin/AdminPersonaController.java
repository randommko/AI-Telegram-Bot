package org.example.Admin;

import org.example.AiChat.PersonaDetails;
import org.example.AiChat.PersonaRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/** Карточки персон: имя, промпт, алиасы и настройки эмоциональности. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminPersonaController {

    private final PersonaRepository personas;
    private final AdminPersonaValidator validator;

    public AdminPersonaController(PersonaRepository personas, AdminPersonaValidator validator) {
        this.personas = personas;
        this.validator = validator;
    }

    @GetMapping("/admin/personas")
    String list(Model model) {
        model.addAttribute("title", "Персоны");
        model.addAttribute("personas", personas.findAllWithCounts());
        return "admin/personas";
    }

    @GetMapping("/admin/personas/new")
    String create(Model model) {
        return form(model, PersonaForm.blank(), null);
    }

    @GetMapping("/admin/personas/{id}")
    String edit(@PathVariable int id, Model model, RedirectAttributes redirect) {
        Optional<PersonaDetails> details = personas.findDetails(id);
        if (details.isEmpty()) {
            redirect.addFlashAttribute("error", "Персона " + id + " не найдена");
            return "redirect:/admin/personas";
        }
        return form(model, PersonaForm.of(details.get()), null);
    }

    @PostMapping("/admin/personas")
    String save(@ModelAttribute PersonaForm form, Model model, RedirectAttributes redirect) {
        AdminPersonaValidator.Result result = validator.validate(form);
        if (result.hasErrors())
            return form(model, form, result.errors());

        PersonaDetails details = result.details();
        if (details.id() == null) {
            personas.create(details);
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» создана");
        } else {
            personas.update(details);
            redirect.addFlashAttribute("saved", "Персона «" + details.name() + "» сохранена");
        }
        return "redirect:/admin/personas";
    }

    @PostMapping("/admin/personas/{id}/delete")
    String delete(@PathVariable int id, RedirectAttributes redirect) {
        List<Long> chats = personas.chatsOf(id);
        if (!chats.isEmpty()) {
            redirect.addFlashAttribute("error",
                    "Персона привязана к чатам " + chats + " — сначала снимите привязку на странице «Чаты»");
            return "redirect:/admin/personas";
        }

        personas.delete(id);
        redirect.addFlashAttribute("saved", "Персона " + id + " удалена");
        return "redirect:/admin/personas";
    }

    private String form(Model model, PersonaForm form, java.util.Map<String, String> errors) {
        model.addAttribute("title", form.id() == null ? "Новая персона" : "Персона " + form.name());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors == null ? java.util.Map.of() : errors);
        return "admin/persona-form";
    }
}
