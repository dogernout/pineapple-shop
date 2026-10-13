package ru.dogernout.pineapple_shop.bot.conversation.flow;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationFlow;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.service.EarnReasonService;

@RequiredArgsConstructor
public class EditEarnReasonFlow implements ConversationFlow {

    public enum Field { DESCRIPTION, POINTS }

    private final Long chatId;
    private final Long adminTelegramId;
    private final Long reasonId;
    private final String reasonTitle;
    private final Field field;
    private final TelegramSender sender;
    private final EarnReasonService earnReasonService;
    private final ConversationManager conversationManager;

    @Override
    public void onEnter() {
        var prompt = switch (field) {
            case DESCRIPTION -> "Новое описание для «%s»? Отправь \"-\", чтобы очистить.";
            case POINTS -> "Новое количество баллов для «%s»? Отправь \"-\" для «по договорённости».";
        };
        sender.send(chatId, prompt.formatted(reasonTitle) + "\nДля отмены нажми любую кнопку меню.");
    }

    @Override
    public void onTextInput(String text) {
        var value = text.strip();
        boolean clear = "-".equals(value);

        Integer points = null;
        if (field == Field.POINTS && !clear) {
            points = parsePositiveInt(value);
            if (points == null) {
                sender.send(chatId, "Нужно ввести положительное целое число или \"-\". Попробуй ещё раз.");
                return; // flow остаётся активным, ждём корректный ввод
            }
        }

        try {
            switch (field) {
                case DESCRIPTION -> {
                    earnReasonService.updateDescription(adminTelegramId, reasonId, clear ? null : value);
                    sender.send(chatId, "✅ Описание обновлено.");
                }
                case POINTS -> {
                    earnReasonService.updatePoints(adminTelegramId, reasonId, points);
                    sender.send(chatId, "✅ Баллы обновлены.");
                }
            }
        } finally {
            conversationManager.finish(chatId);
        }
    }

    private Integer parsePositiveInt(String text) {
        try {
            int v = Integer.parseInt(text);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
