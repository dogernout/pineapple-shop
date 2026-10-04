package ru.dogernout.pineapple_shop.bot.conversation.flow;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationFlow;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.service.EarnReasonService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

@RequiredArgsConstructor
public class AddEarnReasonFlow implements ConversationFlow {

    private enum Step { TITLE, DESCRIPTION, POINTS, CONFIRM }

    private final Long chatId;
    private final Long adminTelegramId;
    private final TelegramSender sender;
    private final EarnReasonService earnReasonService;
    private final ConversationManager conversationManager;

    private Step step = Step.TITLE;
    private String title;
    private String description;
    private Integer points;

    @Override
    public void onEnter() {
        sender.send(chatId, "Название способа заработать баллы?");
    }

    @Override
    public void onTextInput(String text) {
        switch (step) {
            case TITLE -> handleTitle(text);
            case DESCRIPTION -> handleDescription(text);
            case POINTS -> handlePoints(text);
            case CONFIRM -> sender.send(chatId, "Нажми кнопку ниже, чтобы подтвердить или отменить.");
        }
    }

    private void handleTitle(String text) {
        if (text.isBlank()) {
            sender.send(chatId, "Название не может быть пустым. Попробуй ещё раз.");
            return;
        }
        this.title = text;
        this.step = Step.DESCRIPTION;
        sender.send(chatId, "Описание? (или \"-\", чтобы пропустить)");
    }

    private void handleDescription(String text) {
        this.description = "-".equals(text.strip()) ? null : text.strip();
        this.step = Step.POINTS;
        sender.send(chatId, "Сколько баллов? (или \"-\", если \"по договорённости\")");
    }

    private void handlePoints(String text) {
        var stripped = text.strip();
        if ("-".equals(stripped)) {
            this.points = null;
        } else {
            var parsed = parsePositiveInt(stripped);
            if (parsed == null) {
                sender.send(chatId, "Нужно ввести положительное целое число или \"-\". Попробуй ещё раз.");
                return;
            }
            this.points = parsed;
        }
        this.step = Step.CONFIRM;

        var summary = "%s\n%s\nБаллы: %s".formatted(
                title, description == null ? "" : description,
                points == null ? "по договорённости" : points.toString());

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Добавить")
                        .callbackData(CallbackData.of(CallbackData.CONFIRM_ADD_EARN_REASON)),
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
        if (!CallbackData.CONFIRM_ADD_EARN_REASON.equals(action) || step != Step.CONFIRM) {
            return;
        }

        try {
            earnReasonService.addReason(adminTelegramId, title, description, points);
            sender.send(chatId, "✅ Добавлено в справочник.");
        } finally {
            conversationManager.finish(chatId);
        }
    }

    private Integer parsePositiveInt(String text) {
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
