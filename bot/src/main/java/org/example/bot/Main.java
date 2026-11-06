package org.example.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.example.bot", "org.example.core"})
// Сканирование свойств не наследует scanBasePackages: без core BotProperties не создаётся.
@ConfigurationPropertiesScan(basePackages = {"org.example.bot", "org.example.core"})
@EnableScheduling
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
