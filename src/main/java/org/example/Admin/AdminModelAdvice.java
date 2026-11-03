package org.example.Admin;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Роль вошедшего нужна шаблонам: от неё зависит меню и доступные кнопки. */
@ControllerAdvice(basePackages = "org.example.Admin")
public class AdminModelAdvice {
    private final AdminAccess access;

    public AdminModelAdvice(AdminAccess access) {
        this.access = access;
    }

    @ModelAttribute("owner")
    public boolean owner() {
        return access.isOwner();
    }
}
