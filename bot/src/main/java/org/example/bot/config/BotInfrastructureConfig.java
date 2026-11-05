package org.example.bot.config;

import org.example.bot.Delay;
import org.example.core.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/** Всё, что нужно только боту: опрос Telegram, прокси и пауза между репликами. */
@Configuration
public class BotInfrastructureConfig {
    private static final Logger logger = LoggerFactory.getLogger(BotInfrastructureConfig.class);

    /** Пауза между репликами; прерывание не должно ронять ответ. */
    @Bean
    Delay delay() {
        return millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Опции Telegram API: прокси из BOT_PROXY_* либо прямое подключение.
     * Метод обязан вернуть объект в любой ветке — null ломает конструктор TelegramLongPollingBot.
     */
    @Bean
    DefaultBotOptions botOptions(BotProperties properties) {
        if (!properties.hasProxy()) {
            logger.info("Прокси для Telegram API не задан — прямое подключение");
            return new DefaultBotOptions();
        }

        BotProperties.Proxy proxy = properties.proxy();
        String type = proxy.type() == null || proxy.type().isBlank() ? "HTTP" : proxy.type();
        String host = proxy.host();
        int port = proxy.port();
        boolean socks = "SOCKS5".equalsIgnoreCase(type) || "SOCKS4".equalsIgnoreCase(type);

        DefaultBotOptions options = new DefaultBotOptions();
        if ("SOCKS5".equalsIgnoreCase(type)) {
            options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
        } else if ("SOCKS4".equalsIgnoreCase(type)) {
            options.setProxyType(DefaultBotOptions.ProxyType.SOCKS4);
        } else {
            options.setProxyType(DefaultBotOptions.ProxyType.HTTP);
        }
        options.setProxyHost(host);
        options.setProxyPort(port);

        // Системные свойства для java.net.http.HttpClient (DeepSeek, гороскоп)
        if (socks) {
            System.setProperty("socksProxyHost", host);
            System.setProperty("socksProxyPort", String.valueOf(port));
        } else {
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", String.valueOf(port));
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", String.valueOf(port));
        }

        if (proxy.user() != null && !proxy.user().isBlank()) {
            String proxyPass = proxy.pass() == null ? "" : proxy.pass();
            java.net.Authenticator.setDefault(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    return new java.net.PasswordAuthentication(proxy.user(), proxyPass.toCharArray());
                }
            });
            logger.info("Прокси {}:{} ({}) с аутентификацией", host, port, type);
        } else {
            logger.info("Прокси {}:{} ({}) без аутентификации", host, port, type);
        }

        return options;
    }

    @Bean
    TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }
}
