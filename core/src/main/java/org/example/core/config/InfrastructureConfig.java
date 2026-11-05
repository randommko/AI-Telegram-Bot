package org.example.core.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
