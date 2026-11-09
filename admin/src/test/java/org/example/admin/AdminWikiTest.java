package org.example.admin;

import org.example.core.config.BotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminWikiController.class,
        properties = {
                "bot.admin.password=secret",
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key.deepseek=sk-test"
        })
@Import(AdminSecurityConfig.class)
@EnableConfigurationProperties(BotProperties.class)
class AdminWikiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccess access;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void wikiIsReadableByModerator() throws Exception {
        when(access.isOwner()).thenReturn(false);

        mockMvc.perform(get("/admin/wiki").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Как бот отвечает")))
                .andExpect(content().string(containsString("Тексты по умолчанию")))
                .andExpect(content().string(containsString("Ты участник этого чата")))
                .andExpect(content().string(containsString("рекомендуемая")))
                .andExpect(content().string(containsString("Вики")));
    }

    @Test
    void wikiHasNoEditableFields() throws Exception {
        when(access.isOwner()).thenReturn(true);

        mockMvc.perform(get("/admin/wiki").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                // Единственное поле на странице — служебный CSRF из кнопки «Выйти» в шапке.
                .andExpect(content().string(not(containsString("<textarea"))))
                .andExpect(content().string(not(containsString("class=\"setting\""))));
    }
}
