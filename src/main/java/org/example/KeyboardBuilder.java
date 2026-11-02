package org.example;

import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

public class KeyboardBuilder {
    private static final Logger logger = LoggerFactory.getLogger(KeyboardBuilder.class);
    private static final int BUTTONS_PER_ROW = 3;

    // Enum для зодиаков (расширяемо, локализуемо)
    public enum Zodiac {
        ARIES("aries", ":aries: Овен", "aries_button_pressed"),
        TAURUS("taurus", ":taurus: Телец", "taurus_button_pressed"),
        GEMINI("gemini", ":gemini: Близнецы", "gemini_button_pressed"),
        CANCER("cancer", ":cancer: Рак", "cancer_button_pressed"),
        LEO("leo", ":leo: Лев", "leo_button_pressed"),
        VIRGO("virgo", ":virgo: Дева", "virgo_button_pressed"),
        LIBRA("libra", ":libra: Весы", "libra_button_pressed"),
        SCORPIO("scorpio", ":scorpius: Скорпион", "scorpio_button_pressed"),
        SAGITTARIUS("sagittarius", ":sagittarius: Стрелец", "sagittarius_button_pressed"),
        CAPRICORN("capricorn", ":capricorn: Козерог", "capricorn_button_pressed"),
        AQUARIUS("aquarius", ":aquarius: Водолей", "aquarius_button_pressed"),
        PISCES("pisces", ":pisces: Рыбы", "pisces_button_pressed");

        private final String id;
        private final String label;
        private final String callbackData;

        Zodiac(String id, String label, String callbackData) {
            this.id = id;
            this.label = label;
            this.callbackData = callbackData;
        }

        public String getId() {
            return this.id;
        }

        public InlineKeyboardButton toButton() {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(EmojiParser.parseToUnicode(label));
            button.setCallbackData(callbackData);
            return button;
        }

        // ⭐ STATIC метод для поиска по callbackData
        public static KeyboardBuilder.Zodiac fromCallback(String callbackData) {
            if (callbackData == null) return null;
            for (Zodiac zodiac : values()) {
                if (zodiac.callbackData.equals(callbackData)) {
                    return zodiac;
                }
            }
            return null;
        }
    }


    /**
     * Строит клавиатуру зодиака (3 кнопки в ряд)
     */
    public InlineKeyboardMarkup buildHoroscopeKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (Zodiac zodiac : Zodiac.values()) {
            currentRow.add(zodiac.toButton());

            if (currentRow.size() == BUTTONS_PER_ROW) {
                rows.add(new ArrayList<>(currentRow)); // Копируем для immutable
                currentRow.clear();
            }
        }

        // Последний неполный ряд
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Отправляет вопрос о сбросе памяти AI с кнопками подтверждения и отказа
     */
    public void sendAiClearQuestion(MessageSender sender, Long chatId, Long authorId, int forgottenCount) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Обнулить память AI в этом чате? Забыто будет " + forgottenCount + " сообщений")
                .replyMarkup(buildAiClearKeyboard(authorId, forgottenCount))
                .build();

        try {
            sender.getBot().execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки вопроса о сбросе памяти AI: ", e);
        }
    }

    public InlineKeyboardMarkup buildAiClearKeyboard(Long authorId, int forgottenCount) {
        InlineKeyboardButton confirm = aiClearButton("Да",
                new AiClearCallback(AiClearCallback.Decision.CONFIRM, authorId, forgottenCount));
        InlineKeyboardButton cancel = aiClearButton("Отмена",
                new AiClearCallback(AiClearCallback.Decision.CANCEL, authorId, forgottenCount));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(confirm, cancel)));
        return markup;
    }

    private InlineKeyboardButton aiClearButton(String text, AiClearCallback callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback.toCallbackData());
        return button;
    }

    /**
     * Отправляет сообщение с клавиатурой зодиака
     */
    public void sendHoroscopeKeyboard(MessageSender sender, Long chatId) {
        InlineKeyboardMarkup keyboard = buildHoroscopeKeyboard();
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Выберите знак зодиака:")
                .replyMarkup(keyboard)
                .build();

        try {
            sender.getBot().execute(message); // Предполагаем доступ к bot через sender
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки клавиатуры зодиака: ", e);
        }
    }
}
