package ru.dogernout.pineapple_shop.bot.support;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Единая точка отправки сообщений в Telegram. Роутеры и flow-классы не работают
 * с TelegramBot напрямую — это упрощает добавление логирования/ретраев в одном месте
 * и избавляет остальной код от многословного API pengrad.
 */
@Component
@RequiredArgsConstructor
public class TelegramSender {

    private final TelegramBot bot;

    public void send(Long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    public void send(Long chatId, String text, Keyboard keyboard) {
        bot.execute(new SendMessage(chatId, text).replyMarkup(keyboard));
    }

    /** keyboard может быть null — тогда у отредактированного сообщения кнопки просто исчезнут. */
    public void edit(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        var edit = new EditMessageText(chatId, messageId, text);
        if (keyboard != null) {
            edit.replyMarkup(keyboard);
        }
        bot.execute(edit);
    }

    /** Убирает "часики" у нажатой inline-кнопки. Нужно вызывать на каждый callback. */
    public void answerCallback(String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
    }
}
