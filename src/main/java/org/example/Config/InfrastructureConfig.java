package org.example.Config;

import com.zaxxer.hikari.HikariDataSource;
import org.example.Delay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
public class InfrastructureConfig {
    private static final Logger logger = LoggerFactory.getLogger(InfrastructureConfig.class);

    /** Часы — бином, чтобы время в промпте можно было зафиксировать в тестах. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

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
     * Имена BOT_DB_* не совпадают с spring.datasource.*, поэтому пул строится из BotProperties.
     * Параметры пула сохранены прежними, чтобы миграция не меняла поведение соединений.
     */
    @Bean
    DataSource dataSource(BotProperties properties) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(properties.db().url())
                .username(properties.db().user())
                .password(properties.db().password())
                .driverClassName("org.postgresql.Driver")
                .build();

        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setIdleTimeout(30000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setConnectionTimeout(30000);
        dataSource.setConnectionTestQuery("SELECT 1");
        dataSource.setValidationTimeout(5000);
        dataSource.addDataSourceProperty("cachePrepStmts", "true");
        dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return dataSource;
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
