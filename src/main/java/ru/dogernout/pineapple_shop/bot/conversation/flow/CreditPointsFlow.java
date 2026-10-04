package ru.dogernout.pineapple_shop.bot.conversation.flow;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationFlow;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.service.PointsService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

@RequiredArgsConstructor
public class CreditPointsFlow implements ConversationFlow {

    private enum Step { AMOUNT, COMMENT, CONFIRM }

    private final Long chatId;
    private final Long adminTelegramId;
    private final Long memberTelegramId;
    private final TelegramSender sender;
    private final PointsService pointsService;
    private final ConversationManager conversationManager;

    private Step step = Step.AMOUNT;
    private Integer amount;
    private String comment;

    @Override
    public void onEnter() {
        sender.send(chatId, "Сколько баллов начислить? Введи число.");
    }

    @Override
    public void onTextInput(String text) {
        switch (step) {
            case AMOUNT -> handleAmount(text);
            case COMMENT -> handleComment(text);
            case CONFIRM -> sender.send(chatId, "Нажми кнопку ниже, чтобы подтвердить или отменить.");
        }
    }

    private void handleAmount(String text) {
        var parsed = parsePositiveInt(text);
        if (parsed == null) {
            sender.send(chatId, "Нужно ввести положительное целое число. Попробуй ещё раз.");
            return;
        }
        this.amount = parsed;
        this.step = Step.COMMENT;
        sender.send(chatId, "За что начисляем? Напиши комментарий (или \"-\", чтобы пропустить).");
    }

    private void handleComment(String text) {
        this.comment = "-".equals(text.strip()) ? null : text.strip();
        this.step = Step.CONFIRM;

        var summary = "Начислить %d баллов?\nКомментарий: %s"
                .formatted(amount, comment == null ? "—" : comment);

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Подтвердить").callbackData(CallbackData.of(CallbackData.CONFIRM_CREDIT)),
                new InlineKeyboardButton("❌ Отмена").callbackData(CallbackData.of(CallbackData.CANCEL))
        );
        sender.send(chatId, summary, keyboard);
    }

    @Override
    public void onCallback(String callbackData) {
        var action = CallbackData.action(callbackData);

        if (CallbackData.CANCEL.equals(action)) {
            conversationManager.finish(chatId);
            sender.send(chatId, "Отменено.");
            return;
        }
        if (!CallbackData.CONFIRM_CREDIT.equals(action) || step != Step.CONFIRM) {
            return;
        }

        try {
            pointsService.credit(adminTelegramId, memberTelegramId, amount, comment);
            // Баланс читаем заново из БД, а не из сущности, которую вернул credit() —
            // она не отражает bulk-update, см. комментарий в начале PointsService.
            int freshBalance = pointsService.getBalance(memberTelegramId);
            sender.send(chatId, "✅ Начислено %d баллов.\nБаланс: %d".formatted(amount, freshBalance));
        } finally {
            conversationManager.finish(chatId);
        }
    }

    private Integer parsePositiveInt(String text) {
        try {
            int value = Integer.parseInt(text.strip());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
