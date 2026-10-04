package ru.dogernout.pineapple_shop.bot.router;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.domain.Item;
import ru.dogernout.pineapple_shop.domain.Purchase;
import ru.dogernout.pineapple_shop.exception.InsufficientBalanceException;
import ru.dogernout.pineapple_shop.exception.OutOfStockException;
import ru.dogernout.pineapple_shop.service.ItemService;
import ru.dogernout.pineapple_shop.service.PointsService;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallbackRouter {

    private final ItemService itemService;
    private final PointsService pointsService;
    private final ConversationManager conversationManager;
    private final TelegramSender sender;

    public void route(CallbackQuery callbackQuery) {
        var chatId = callbackQuery.message().chat().id();
        var messageId = callbackQuery.message().messageId();
        var telegramUserId = callbackQuery.from().id();
        var data = callbackQuery.data();
        var action = CallbackData.action(data);

        sender.answerCallback(callbackQuery.id());

        // Подтверждение/отмена активного FSM-сценария (например, CreditPointsFlow) —
        // отдаём обработку ему, а не разбираем здесь.
        if (conversationManager.hasActive(chatId)
                && (CallbackData.CONFIRM_CREDIT.equals(action) || CallbackData.CANCEL.equals(action))) {
            conversationManager.get(chatId).ifPresent(flow -> flow.onCallback(data));
            return;
        }

        switch (action) {
            case CallbackData.ITEM_SELECT ->
                    showItemCard(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.CONFIRM_BUY ->
                    handleBuy(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.BACK_TO_CATALOG -> showCatalog(chatId, messageId);
            default -> sender.send(chatId, "Эта кнопка больше не активна.");
        }
    }

    private void showCatalog(Long chatId, Integer messageId) {
        var items = itemService.listAvailable();
        if (items.isEmpty()) {
            sender.edit(chatId, messageId, "Пока нет доступных товаров 🤷", null);
            return;
        }
        sender.edit(chatId, messageId, "Доступные товары:", CatalogKeyboards.catalogList(items));
    }

    private void showItemCard(Long chatId, Integer messageId, Long telegramUserId, Long itemId) {
        var item = itemService.getById(itemId);
        int balance = pointsService.getBalance(telegramUserId);

        var text = "%s\n%s\nЦена: %d баллов\nВ наличии: %d шт.\nТвой баланс: %d баллов".formatted(
                item.getTitle(),
                item.getDescription() == null ? "" : item.getDescription(),
                item.getPrice(), item.getQuantity(), balance);

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Купить").callbackData(CallbackData.of(CallbackData.CONFIRM_BUY, itemId)),
                new InlineKeyboardButton("⬅️ Назад к списку").callbackData(CallbackData.of(CallbackData.BACK_TO_CATALOG))
        );
        sender.edit(chatId, messageId, text, keyboard);
    }

    private void handleBuy(Long chatId, Integer messageId, Long telegramUserId, Long itemId) {
        try {
            var purchase = pointsService.buy(telegramUserId, itemId);
            int freshBalance = pointsService.getBalance(telegramUserId);
            sender.edit(chatId, messageId, "✅ Готово! Ты купил «%s» за %d баллов.\nОстаток баллов: %d"
                    .formatted(purchase.getItem().getTitle(), purchase.getPrice(), freshBalance), null);
        } catch (InsufficientBalanceException e) {
            sender.edit(chatId, messageId, "❌ Недостаточно баллов для этой покупки.", null);
        } catch (OutOfStockException e) {
            sender.edit(chatId, messageId, "❌ Этот товар только что закончился. Загляни в каталог ещё раз.", null);
        }
    }
}
