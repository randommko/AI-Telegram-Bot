package org.example.AiChat;

import java.util.List;

/** Действующее состояние персоны: строки эмоций и вычисленный уровень агрессии. */
public record EmotionState(List<Line> lines, int aggression, String aggressionTitle, String aggressionPhrase) {

    /** Одна эмоция: значения 0..10, отклонение от характера и фраза по полосе. */
    public record Line(String code, String title, int baseValue, int value, int deviation, String phrase) {
    }
}
