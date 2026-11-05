package org.example.admin;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Админка существует только при заданном пароле. Пустое значение — это тоже «выключено»:
 * @ConditionalOnProperty считал бы пустую строку присутствующей и пустил бы в админку без пароля.
 */
public class AdminPasswordPresentCondition implements Condition {
    static final String PROPERTY = "bot.admin.password";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String password = context.getEnvironment().getProperty(PROPERTY);
        return password != null && !password.isBlank();
    }
}
