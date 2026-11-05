package org.example.Admin;

import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Вход в админку и стартовая страница. Остальные разделы подключаются своими контроллерами. */
@Controller
@Conditional(AdminPasswordPresentCondition.class)
public class AdminController {

    /**
     * Отдельной страницы обзора нет, поэтому старт зависит от роли: владельцу открываются
     * настройки, модератору — его первый раздел, иначе вход заканчивался бы отказом 403.
     */
    @GetMapping("/admin")
    String root(Authentication authentication) {
        boolean owner = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + AdminPrincipal.ROLE_ADMIN)::equals);
        return owner ? "redirect:/admin/settings" : "redirect:/admin/personas";
    }

    @GetMapping("/admin/login")
    String login(Model model) {
        model.addAttribute("title", "Вход");
        return "admin/login";
    }
}
