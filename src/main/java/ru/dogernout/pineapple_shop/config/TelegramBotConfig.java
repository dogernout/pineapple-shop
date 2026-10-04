package ru.dogernout.pineapple_shop.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PineappleShopProperties.class)
public class TelegramBotConfig {

    @Bean
    public TelegramBot telegramBot(PineappleShopProperties properties) {
        return new TelegramBot(properties.getToken());
    }
}
