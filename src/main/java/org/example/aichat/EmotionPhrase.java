package org.example.aichat;

/** Фраза «как отвечать» для полосы значения: 0-1, 2-4, 5-6, 7-8, 9-10. */
public record EmotionPhrase(String emotionCode, int fromValue, int toValue, String phrase) {

    public boolean covers(int value) {
        return value >= fromValue && value <= toValue;
    }
}
