package ru.dogernout.pineapple_shop.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Запускает long polling только после того, как ВЕСЬ контекст Spring полностью
 * поднят (ApplicationReadyEvent). Это и разрывает цикл: TelegramBot как бин больше
 * ни от кого не зависит, а polling стартует отдельным шагом уже после сборки всех бинов.
 */
@Component
@RequiredArgsConstructor
public class TelegramBotInitializer {

    private final TelegramBot bot;
    private final PineappleShopDispatcher dispatcher;

    @EventListener(ApplicationReadyEvent.class)
    public void startPolling() {
        bot.setUpdatesListener(updates -> {
            for (var update : updates) {
                dispatcher.dispatch(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }
}
