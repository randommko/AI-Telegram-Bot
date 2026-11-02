package org.example.Admin;

import org.example.Config.BotProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminController.class, AdminSectionsController.class},
        properties = {
                "bot.admin.password=" + AdminSecurityTest.PASSWORD,
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key=sk-test"
        })
@Import(AdminSecurityConfig.class)
@EnableConfigurationProperties(BotProperties.class)
class AdminSecurityTest {
    static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsToLoginWithoutSession() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void showsLoginPage() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Вход в админку")));
    }

    @Test
    void logsInWithCorrectPassword() throws Exception {
        mockMvc.perform(formLogin("/admin/login").user("admin").password(PASSWORD))
                .andExpect(authenticated().withUsername("admin"));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(formLogin("/admin/login").user("admin").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void opensOverviewAfterLogin() throws Exception {
        mockMvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Персоны")));
    }

    @Test
    void rejectsPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/logout").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rendersEverySectionFromTheMenu() throws Exception {
        // «Настройки» подключает свой контроллер и проверяется в AdminSettingsTest.
        for (String path : new String[]{"/admin/personas", "/admin/memories", "/admin/chats"}) {
            mockMvc.perform(get(path).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void logsOutWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/logout").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/login?logout"));
    }
}
