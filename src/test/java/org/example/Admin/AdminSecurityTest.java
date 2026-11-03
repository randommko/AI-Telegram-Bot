package org.example.Admin;

import org.example.Config.BotProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@WebMvcTest(controllers = AdminController.class,
        properties = {
                "bot.admin.password=" + AdminSecurityTest.PASSWORD,
                "bot.token=1:dummy",
                "bot.db.url=jdbc:postgresql://localhost:5432/telegram_bot_db",
                "bot.db.user=app_user",
                "bot.db.password=secret",
                "bot.ai.key=sk-test"
        })
@Import({AdminSecurityConfig.class, AdminUserDetailsService.class})
@EnableConfigurationProperties(BotProperties.class)
class AdminSecurityTest {
    static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AdminAccountsRepository accounts;

    @MockitoBean
    private AdminAccess access;

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
    void opensSettingsAfterLogin() throws Exception {
        mockMvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"));
    }

    @Test
    void rejectsPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/logout").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsOutWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/logout").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/login?logout"));
    }

    @Test
    void moderatorLogsInWithAccountFromDatabase() throws Exception {
        org.mockito.Mockito.when(accounts.findByLogin("vasya")).thenReturn(java.util.Optional.of(
                new AdminAccountsRepository.AdminCredentials(1, 777L, "vasya",
                        passwordEncoder.encode("modpass"))));

        mockMvc.perform(formLogin("/admin/login").user("vasya").password("modpass"))
                .andExpect(authenticated().withUsername("vasya"));
    }

    @Test
    void moderatorIsForbiddenFromOwnerSections() throws Exception {
        mockMvc.perform(get("/admin/settings").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/emotions").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/prompt").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/moderators").with(user("vasya").roles("MODERATOR")))
                .andExpect(status().isForbidden());
    }
}
