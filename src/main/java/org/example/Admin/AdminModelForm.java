package org.example.Admin;

import java.util.Set;

/**
 * Служебные поля формы настроек: участвуют в выборе модели и в проверке, но не являются
 * настройками и не сохраняются в bot_settings — контроллер собирает из них пару aiRoute + aiModel.
 */
final class AdminModelForm {
    /** Радиокнопки режима: прямой список моделей или каскад провайдера. */
    static final String ACCESS_KEY = "aiAccess";
    static final String PROVIDER_KEY = "aiProvider";
    static final String GROUP_KEY = "aiModelGroup";
    static final String DOOR_MODEL_KEY = "aiDoorModel";

    static final String DIRECT = "direct";
    static final String PROVIDER = "provider";

    static final Set<String> KEYS = Set.of(ACCESS_KEY, PROVIDER_KEY, GROUP_KEY, DOOR_MODEL_KEY);

    private AdminModelForm() {
    }
}
