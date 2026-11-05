package org.example.bot;

/** Пауза между репликами ответа: вынесена в бин, чтобы тесты не спали по-настоящему. */
public interface Delay {
    void pause(long millis);
}
