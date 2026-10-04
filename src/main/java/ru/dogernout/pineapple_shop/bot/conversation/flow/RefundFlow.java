package ru.dogernout.pineapple_shop.bot.conversation.flow;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationFlow;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.domain.Purchase;
import ru.dogernout.pineapple_shop.service.PointsService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

@RequiredArgsConstructor
public class RefundFlow implements ConversationFlow {

    private enum Step { REASON, CONFIRM }

    private final Long chatId;
    private final Long adminTelegramId;
    private final Long purchaseId;
    private final TelegramSender sender;
    private final PointsService pointsService;
    private final ConversationManager conversationManager;

    private Step step = Step.REASON;
    private String reason;

    @Override
    public void onEnter() {
        sender.send(chatId, "Причина возврата? (или \"-\", чтобы пропустить)");
    }

    @Override
    public void onTextInput(String text) {
        if (step == Step.CONFIRM) {
            sender.send(chatId, "Нажми кнопку ниже, чтобы подтвердить или отменить.");
            return;
        }

        this.reason = "-".equals(text.strip()) ? null : text.strip();
        this.step = Step.CONFIRM;

        var purchase = pointsService.getPurchase(purchaseId);
        var summary = "Вернуть «%s», %d баллов?".formatted(
                purchase.getItem().getTitle(), purchase.getPrice());

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Подтвердить").callbackData(CallbackData.of(CallbackData.CONFIRM_REFUND)),
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
        if (!CallbackData.CONFIRM_REFUND.equals(action) || step != Step.CONFIRM) {
            return;
        }

        try {
            pointsService.refund(adminTelegramId, purchaseId, reason);
            sender.send(chatId, "✅ Возврат выполнен. Баллы и остаток товара восстановлены.");
        } finally {
            conversationManager.finish(chatId);
        }
    }
}
