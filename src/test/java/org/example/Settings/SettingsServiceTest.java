package org.example.Settings;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.example.Settings.Settings.DEFAULT_AI_MODEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    @Test
    void readsIntegerFromSetting() throws Exception {
        assertEquals(7, serviceReturning("7").getInt("любая", 42));
    }

    @Test
    void fallsBackToDefaultForGarbageInteger() throws Exception {
        assertEquals(42, serviceReturning("abc").getInt("любая", 42));
    }

    @Test
    void fallsBackToDefaultForMissingInteger() throws Exception {
        assertEquals(42, serviceReturning(null).getInt("любая", 42));
        assertEquals(42, serviceReturning("   ").getInt("любая", 42));
    }

    @Test
    void readsFloatWithTrailingSuffix() throws Exception {
        assertEquals(1.8f, serviceReturning("1.8F").getFloat("любая", 1.0f));
    }

    @Test
    void fallsBackToDefaultForGarbageFloat() throws Exception {
        assertEquals(1.0f, serviceReturning("горячо").getFloat("любая", 1.0f));
    }

    @Test
    void readsConfiguredModel() throws Exception {
        assertEquals("deepseek-chat", serviceReturning("deepseek-chat").getModel());
    }

    @Test
    void fallsBackToDefaultModel() throws Exception {
        assertEquals(DEFAULT_AI_MODEL, serviceReturning(null).getModel());
        assertEquals(DEFAULT_AI_MODEL, serviceReturning("  ").getModel());
    }

    private SettingsService serviceReturning(String value) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(value != null);
        when(resultSet.getString("value")).thenReturn(value);

        return new SettingsService(dataSource);
    }
}
