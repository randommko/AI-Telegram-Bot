package org.example.Admin;

import org.example.Config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Вход в админку: владелец паролем из окружения, модераторы — учётками из базы.
 * Без BOT_ADMIN_PASSWORD админских страниц нет вовсе, поэтому цепочка разрешает всё:
 * запрос к /admin/** получает 404 от Spring MVC, а не редирект на форму входа,
 * которой тоже не существует.
 *
 * Состояние админки пишется в лог при создании бина, а не в ApplicationRunner:
 * так строка появляется даже когда бот не смог зарегистрироваться.
 */
@Configuration
public class AdminSecurityConfig {
    public static final String ADMIN_USERNAME = "admin";
    private static final String DEFAULT_BIND = "127.0.0.1";
    private static final int DEFAULT_PORT = 8081;
    private static final Logger logger = LoggerFactory.getLogger(AdminSecurityConfig.class);

    @Bean
    PasswordEncoder adminPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, BotProperties properties,
                                                 AdminUserDetailsService admins) throws Exception {
        if (!properties.hasAdmin()) {
            logger.warn("Админка выключена: не задан BOT_ADMIN_PASSWORD — запросы к /admin/** получат 404");
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .logout(AbstractHttpConfigurer::disable);
            return http.build();
        }

        logEnabled(properties.admin());

        http.userDetailsService(admins)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/admin/login", "/admin/logout").permitAll()
                        // Разделы владельца закрыты ролью, а не только скрытым пунктом меню.
                        .requestMatchers("/admin/settings/**", "/admin/emotions/**",
                                "/admin/prompt/**", "/admin/moderators/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .failureUrl("/admin/login?error")
                        .defaultSuccessUrl("/admin", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .permitAll());
        return http.build();
    }

    private void logEnabled(BotProperties.Admin admin) {
        String bind = admin.bind() == null || admin.bind().isBlank() ? DEFAULT_BIND : admin.bind();
        int port = admin.port() == null ? DEFAULT_PORT : admin.port();

        if (admin.bind() != null && admin.bind().isBlank())
            logger.warn("BOT_ADMIN_BIND пуст: админка слушает все интерфейсы, а не только loopback");
        logger.info("Админка включена на {}:{}", bind, port);
    }
}
