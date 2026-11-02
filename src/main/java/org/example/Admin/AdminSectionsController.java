package org.example.Admin;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Заглушки разделов: меню каркаса должно вести на существующие страницы.
 * Маршрут переезжает в свой контроллер вместе с готовым разделом.
 */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
class AdminSectionsController {

    @GetMapping("/admin/personas")
    String personas(Model model) {
        return placeholder(model, "Персоны");
    }

    @GetMapping("/admin/memories")
    String memories(Model model) {
        return placeholder(model, "Воспоминания");
    }

    @GetMapping("/admin/chats")
    String chats(Model model) {
        return placeholder(model, "Чаты");
    }

    private String placeholder(Model model, String title) {
        model.addAttribute("title", title);
        return "admin/section";
    }
}
