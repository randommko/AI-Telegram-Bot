package org.example.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"org.example.admin", "org.example.core"})
// Сканирование свойств не наследует scanBasePackages: dataSource и вход читают BotProperties из core.
@ConfigurationPropertiesScan(basePackages = {"org.example.admin", "org.example.core"})
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
