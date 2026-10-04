package ru.dogernout.pineapple_shop.bot.conversation.flow;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationFlow;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.service.ItemService;

/**
 * Самый короткий flow — один вопрос, без подтверждения кнопкой. Для операции
 * "довезли ещё пару экземпляров" полноценный шаг подтверждения избыточен.
 */
@RequiredArgsConstructor
public class RestockFlow implements ConversationFlow {

    private final Long chatId;
    private final Long adminTelegramId;
    private final Long itemId;
    private final String itemTitle;
    private final TelegramSender sender;
    private final ItemService itemService;
    private final ConversationManager conversationManager;

    @Override
    public void onEnter() {
        sender.send(chatId, "На сколько штук пополнить «%s»?".formatted(itemTitle));
    }

    @Override
    public void onTextInput(String text) {
        if ("-".equals(text.strip())) {
            conversationManager.finish(chatId);
            sender.send(chatId, "Отменено.");
            return;
        }

        var amount = parsePositiveInt(text);
        if (amount == null) {
            sender.send(chatId, "Нужно ввести положительное целое число (или \"-\" для отмены). Попробуй ещё раз.");
            return;
        }

        try {
            itemService.restock(adminTelegramId, itemId, amount);
            sender.send(chatId, "✅ Остаток «%s» пополнен на %d шт.".formatted(itemTitle, amount));
        } finally {
            conversationManager.finish(chatId);
        }
    }

    private Integer parsePositiveInt(String text) {  // TODO: move to utils
        try {
            int value = Integer.parseInt(text.strip());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
