package ru.dogernout.pineapple_shop.config;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.dogernout.pineapple_shop.bot.PineappleShopDispatcher;

@Configuration
@EnableConfigurationProperties(PineappleShopProperties.class)
public class TelegramBotConfig {

    @Bean
    public TelegramBot telegramBot(PineappleShopProperties properties, PineappleShopDispatcher dispatcher) {
        TelegramBot bot = new TelegramBot(properties.getToken());

        // Long polling: библиотека сама крутит цикл getUpdates в фоновом потоке
        // и отдаёт нам апдейты пачками. Подтверждаем ВСЕ апдейты сразу — при таком
        // малом трафике (единицы сообщений в неделю) ретраи/дедупликация не нужны.
        bot.setUpdatesListener(updates -> {
            for (var update : updates) {
                dispatcher.dispatch(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        return bot;
    }
}
