package org.example.bot;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotLifecycleTest {

    private final TelegramBotsApi botsApi = mock(TelegramBotsApi.class);
    private final TelegramLongPollingBot bot = mock(TelegramLongPollingBot.class);

    @Test
    void registersOnSecondAttemptAfterFailure() throws TelegramApiException {
        BotSession session = mock(BotSession.class);
        when(session.isRunning()).thenReturn(true);
        when(botsApi.registerBot(bot))
                .thenThrow(new TelegramApiException("сеть недоступна"))
                .thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(botsApi, bot);
        lifecycle.start();

        verify(botsApi, times(2)).registerBot(bot);
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void failsAfterThreeUnsuccessfulAttempts() throws TelegramApiException {
        when(botsApi.registerBot(bot)).thenThrow(new TelegramApiException("сеть недоступна"));

        assertThatThrownBy(() -> new BotLifecycle(botsApi, bot).start())
                .isInstanceOf(IllegalStateException.class);

        verify(botsApi, times(3)).registerBot(bot);
    }

    @Test
    void stopsPollingOnShutdown() throws TelegramApiException {
        BotSession session = mock(BotSession.class);
        when(session.isRunning()).thenReturn(true);
        when(botsApi.registerBot(bot)).thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(botsApi, bot);
        lifecycle.start();
        lifecycle.stop();

        verify(session).stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void runsShutdownCallbackEvenWhenSessionStopFails() throws TelegramApiException {
        BotSession session = mock(BotSession.class);
        when(session.isRunning()).thenReturn(true);
        doThrow(new IllegalStateException("Session already stopped")).when(session).stop();
        when(botsApi.registerBot(bot)).thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(botsApi, bot, Duration.ofSeconds(1));
        lifecycle.start();

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        lifecycle.stop(() -> callbackRan.set(true));

        assertThat(callbackRan).isTrue();
    }

    @Test
    void doesNotBlockShutdownWhenSessionStopHangs() throws Exception {
        CountDownLatch neverReleased = new CountDownLatch(1);
        BotSession session = mock(BotSession.class);
        when(session.isRunning()).thenReturn(true);
        doAnswer(invocation -> {
            neverReleased.await(5, TimeUnit.SECONDS);
            return null;
        }).when(session).stop();
        when(botsApi.registerBot(bot)).thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(botsApi, bot, Duration.ofMillis(200));
        lifecycle.start();

        long startedAt = System.currentTimeMillis();
        lifecycle.stop();
        long elapsed = System.currentTimeMillis() - startedAt;

        assertThat(elapsed).isLessThan(2000L);
    }
}
