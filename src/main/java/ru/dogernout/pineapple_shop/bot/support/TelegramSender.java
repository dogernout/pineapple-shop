package ru.dogernout.pineapple_shop.bot.support;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Единая точка отправки сообщений в Telegram. Роутеры и flow-классы не работают
 * с TelegramBot напрямую — это упрощает добавление логирования/ретраев в одном месте
 * и избавляет остальной код от многословного API pengrad.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSender {

    private final TelegramBot bot;

    public void send(Long chatId, String text) {
        execute(new SendMessage(chatId, text));
    }

    public void send(Long chatId, String text, Keyboard keyboard) {
        execute(new SendMessage(chatId, text).replyMarkup(keyboard));
    }

    /** keyboard может быть null — тогда у отредактированного сообщения кнопки просто исчезнут. */
    public void edit(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        var edit = new EditMessageText(chatId, messageId, text);
        if (keyboard != null) {
            edit.replyMarkup(keyboard);
        }
        execute(edit);
    }

    /** Убирает "часики" у нажатой inline-кнопки. Нужно вызывать на каждый callback. */
    public void answerCallback(String callbackQueryId) {
        execute(new AnswerCallbackQuery(callbackQueryId));
    }

    private <T extends BaseRequest<T, R>, R extends BaseResponse> R execute(T request) {
        var response = bot.execute(request);
        if (response.isOk()) {
            log.info("Telegram {} ok", request.getMethod());
        } else {
            log.error("Telegram {} failed: code={} description={}",
                    request.getMethod(), response.errorCode(), response.description());
        }
        return response;
    }
}
