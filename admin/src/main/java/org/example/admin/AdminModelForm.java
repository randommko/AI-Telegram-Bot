package org.example.admin;

import java.util.Set;

/**
 * Служебные поля формы настроек: участвуют в выборе модели и в проверке, но не являются
 * настройками и не сохраняются в bot_settings — контроллер собирает из них пару aiRoute + aiModel.
 * Второй набор — для модели реакций на вкладке «Реакции».
 */
final class AdminModelForm {
    /** Радиокнопки режима: прямой список моделей или каскад провайдера. */
    static final String ACCESS_KEY = "aiAccess";
    static final String PROVIDER_KEY = "aiProvider";
    static final String GROUP_KEY = "aiModelGroup";
    static final String DOOR_MODEL_KEY = "aiDoorModel";

    static final String REACTION_ACCESS_KEY = "aiReactionAccess";
    static final String REACTION_PROVIDER_KEY = "aiReactionProvider";
    static final String REACTION_GROUP_KEY = "aiReactionGroup";
    static final String REACTION_DOOR_MODEL_KEY = "aiReactionDoorModel";

    static final String DIRECT = "direct";
    static final String PROVIDER = "provider";

    static final Set<String> KEYS = Set.of(ACCESS_KEY, PROVIDER_KEY, GROUP_KEY, DOOR_MODEL_KEY,
            REACTION_ACCESS_KEY, REACTION_PROVIDER_KEY, REACTION_GROUP_KEY, REACTION_DOOR_MODEL_KEY);

    private AdminModelForm() {
    }
}
