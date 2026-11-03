package org.example.Admin;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Вход в админку и стартовая страница. Остальные разделы подключаются своими контроллерами. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminController {

    /** Стартовая страница — настройки: отдельной страницы обзора больше нет. */
    @GetMapping("/admin")
    String root() {
        return "redirect:/admin/settings";
    }

    @GetMapping("/admin/login")
    String login(Model model) {
        model.addAttribute("title", "Вход");
        return "admin/login";
    }
}
