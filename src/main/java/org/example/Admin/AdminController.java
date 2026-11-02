package org.example.Admin;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Обзор и форма входа. Остальные разделы подключаются своими контроллерами. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminController {

    @GetMapping("/admin")
    String overview(Model model) {
        model.addAttribute("title", "Обзор");
        return "admin/overview";
    }

    @GetMapping("/admin/login")
    String login(Model model) {
        model.addAttribute("title", "Вход");
        return "admin/login";
    }
}
