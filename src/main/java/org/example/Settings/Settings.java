package org.example.Settings;

import java.util.List;

public class Settings {
    public static final String AI_CONTEXT = "aiPromtContext";
    public static final String AI_ANSWER_TEMPERATURE = "aiAnswerTemperature";
    public static final String AI_MAX_TOKENS_ANSWER_QUESTION = "maxTokensQuestion";
    public static final String AI_HISTORY_SIZE = "aiHistorySize";
    public static final String AI_MEMORY_PROMPT_SIZE = "aiMemoryPromptSize";
    public static final String AI_MEMORY_ANALYZE_EVERY = "aiMemoryAnalyzeEvery";
    public static final String AI_MEMORY_EXTRACT_PROMPT = "aiMemoryExtractPrompt";
    /** Сколько последних новых сообщений уходит в один запрос анализа памяти. */
    public static final String AI_MEMORY_ANALYSIS_MESSAGES = "aiMemoryAnalysisMessages";
    /** Бюджет токенов на ответ анализа памяти: reasoning-модель тратит его и на размышления. */
    public static final String AI_MEMORY_ANALYSIS_MAX_TOKENS = "aiMemoryAnalysisMaxTokens";
    public static final String OWNER_CHAT_ID = "ownerChatId";
    /** Ключ настройки с именем модели; значение по умолчанию — DEFAULT_AI_MODEL. */
    public static final String AI_MODEL_KEY = "aiModel";
    public static final String DEFAULT_AI_MODEL = "deepseek-flash";
    /** Модели, которые умеет клиент: список меняется вместе с кодом, а не с данными. */
    public static final List<String> AVAILABLE_AI_MODELS = List.of("deepseek-chat", "deepseek-flash");
    public static final String AI_PERSONA_JOIN_TEMPLATE = "aiPersonaJoinTemplate";
    public static final String AI_PERSONA_LEAVE_TEMPLATE = "aiPersonaLeaveTemplate";
    /** Разделы системного промпта: порядок и бюджеты — в коде, текст — в настройках. */
    public static final String AI_PROMPT_TECHNICAL = "aiPromptTechnical";
    public static final String AI_PROMPT_EMOTIONS_HEADER = "aiPromptEmotionsHeader";
    public static final String AI_PROMPT_GRUDGES_HEADER = "aiPromptGrudgesHeader";
    public static final String AI_PROMPT_MEMORY_CORE_HEADER = "aiPromptMemoryCoreHeader";
    public static final String AI_PROMPT_MEMORY_REGULAR_HEADER = "aiPromptMemoryRegularHeader";
    public static final String AI_PROMPT_PEOPLE_HEADER = "aiPromptPeopleHeader";
    public static final String AI_PROMPT_TIME_TEMPLATE = "aiPromptTimeTemplate";
    public static final String AI_PROMPT_TIME_ZONE = "aiPromptTimeZone";
    public static final String AI_PROMPT_ANSWER_FORMAT = "aiPromptAnswerFormat";
    /** Бюджет токенов шага «план»: reasoning-модель тратит его и на размышления. */
    public static final String AI_PLAN_MAX_TOKENS = "aiPlanMaxTokens";
    /** Обиды: прибавки за оскорбление, скорость затухания и порог адресата. */
    public static final String AI_GRUDGE_INSULT_STRENGTH = "aiGrudgeInsultStrength";
    public static final String AI_GRUDGE_ANGER_DELTA = "aiGrudgeAngerDelta";
    public static final String AI_GRUDGE_DECAY_PER_HOUR = "aiGrudgeDecayPerHour";
    public static final String AI_GRUDGE_THRESHOLD = "aiGrudgeThreshold";
    public static final Long BOT_ID_PROD = 7332966399L;
    public static final Long BOT_ID_TEST = 7712595730L;
    public static final Long MY_CHAT_ID = -1001347887839L;
    public final static Long AI_ID = 0L;
    public final static String AI_SYSTEM_ROLE = "system";
    public final static String AI_ASSISTANT_ROLE = "assistant";
    public final static String USER_ROLE = "user";
    public final static String DEEP_SEEK_BASE_URL = "https://api.deepseek.com/v1/chat/completions";
}
