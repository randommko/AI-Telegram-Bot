package org.example.bot;

import org.example.core.config.BotProperties;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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

    private static final String TOKEN = "123:token";

    private final TelegramBotsLongPollingApplication application = mock(TelegramBotsLongPollingApplication.class);
    private final LongPollingSingleThreadUpdateConsumer bot = mock(LongPollingSingleThreadUpdateConsumer.class);
    private final BotProperties properties = new BotProperties(TOKEN, null, null, null, null);

    @Test
    void registersOnSecondAttemptAfterFailure() throws TelegramApiException {
        BotSession session = mock(BotSession.class);
        when(session.isRunning()).thenReturn(true);
        when(application.registerBot(TOKEN, bot))
                .thenThrow(new TelegramApiException("сеть недоступна"))
                .thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(application, bot, properties);
        lifecycle.start();

        verify(application, times(2)).registerBot(TOKEN, bot);
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void failsAfterThreeUnsuccessfulAttempts() throws TelegramApiException {
        when(application.registerBot(TOKEN, bot)).thenThrow(new TelegramApiException("сеть недоступна"));

        assertThatThrownBy(() -> new BotLifecycle(application, bot, properties).start())
                .isInstanceOf(IllegalStateException.class);

        verify(application, times(3)).registerBot(TOKEN, bot);
    }

    @Test
    void stopsPollingOnShutdown() throws TelegramApiException {
        BotSession session = mock(BotSession.class);
        when(session.isRunning()).thenReturn(true);
        when(application.registerBot(TOKEN, bot)).thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(application, bot, properties);
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
        when(application.registerBot(TOKEN, bot)).thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(application, bot, properties, Duration.ofSeconds(1));
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
        when(application.registerBot(TOKEN, bot)).thenReturn(session);

        BotLifecycle lifecycle = new BotLifecycle(application, bot, properties, Duration.ofMillis(200));
        lifecycle.start();

        long startedAt = System.currentTimeMillis();
        lifecycle.stop();
        long elapsed = System.currentTimeMillis() - startedAt;

        assertThat(elapsed).isLessThan(2000L);
    }
}
