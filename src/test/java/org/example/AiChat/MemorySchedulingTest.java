package org.example.AiChat;

import org.example.Settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Проверяет, что фоновый анализ действительно запускается планировщиком, а не только объявлен. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MemorySchedulingTest.SchedulingConfig.class)
class MemorySchedulingTest {

    @Autowired
    private PersonaRepository personaRepository;

    @Test
    void applicationEnablesScheduling() {
        assertNotNull(org.example.Main.class.getAnnotation(EnableScheduling.class));
    }

    @Test
    void scheduledAnalysisRunsOnItsOwn() {
        verify(personaRepository, timeout(5000)).findAssignedChats();
    }

    @Configuration
    @EnableScheduling
    static class SchedulingConfig {

        @Bean
        PersonaRepository personaRepository() {
            PersonaRepository repository = mock(PersonaRepository.class);
            when(repository.findAssignedChats()).thenReturn(List.of());
            return repository;
        }

        @Bean
        MemoryRepository memoryRepository() {
            return mock(MemoryRepository.class);
        }

        @Bean
        DeepSeekClient deepSeekClient() {
            return mock(DeepSeekClient.class);
        }

        @Bean
        SettingsService settingsService() {
            return mock(SettingsService.class);
        }

        @Bean
        AiMemoryExtractor aiMemoryExtractor(PersonaRepository personaRepository, MemoryRepository memoryRepository,
                                           DeepSeekClient deepSeekClient, SettingsService settingsService) {
            return new AiMemoryExtractor(personaRepository, memoryRepository,
                    new MemoryExtractionParser(), deepSeekClient, settingsService);
        }
    }
}
