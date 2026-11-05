package org.example.bot;

import org.example.core.aichat.LlmCatalog;
import org.example.core.config.BotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderKeyStatusPublisherTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement statement;
    private BotProperties properties;
    private BotProperties.Ai ai;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        properties = mock(BotProperties.class);
        ai = mock(BotProperties.Ai.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(properties.ai()).thenReturn(ai);
        when(ai.keyFor(anyString())).thenReturn(null);
    }

    @Test
    void statusesTellKeyPresenceForEveryProvider() {
        when(ai.keyFor("deepseek")).thenReturn("sk-test");

        List<ProviderKeyStatusPublisher.Status> statuses =
                new ProviderKeyStatusPublisher(dataSource, properties).statuses();

        assertEquals(LlmCatalog.providers().size(), statuses.size());
        assertTrue(statuses.stream()
                .filter(status -> status.providerId().equals("deepseek"))
                .findFirst().orElseThrow().hasKey());
        assertTrue(statuses.stream()
                .filter(status -> status.providerId().equals("grok"))
                .findFirst().orElseThrow().hasKey() == false);
    }

    @Test
    void publishUpsertsEveryProviderInOneBatch() throws Exception {
        new ProviderKeyStatusPublisher(dataSource, properties).publish();

        verify(statement, times(LlmCatalog.providers().size())).addBatch();
        verify(statement).executeBatch();
        verify(statement, times(LlmCatalog.providers().size())).setString(org.mockito.ArgumentMatchers.eq(1), anyString());
    }
}
