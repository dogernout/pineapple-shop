package ru.dogernout.pineapple_shop.bot.router;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.conversation.flow.AddEarnReasonFlow;
import ru.dogernout.pineapple_shop.bot.conversation.flow.EditEarnReasonFlow;
import ru.dogernout.pineapple_shop.bot.conversation.flow.RefundFlow;
import ru.dogernout.pineapple_shop.bot.conversation.flow.RestockFlow;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.MemberNotifier;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.domain.Item;
import ru.dogernout.pineapple_shop.domain.User;
import ru.dogernout.pineapple_shop.exception.InsufficientBalanceException;
import ru.dogernout.pineapple_shop.exception.OutOfStockException;
import ru.dogernout.pineapple_shop.service.EarnReasonService;
import ru.dogernout.pineapple_shop.service.ItemService;
import ru.dogernout.pineapple_shop.service.PointsService;
import ru.dogernout.pineapple_shop.service.UserService;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallbackRouter {

    // Действия, которые всегда принадлежат активному FSM-сценарию, а не разбираются
    // здесь напрямую. CONFIRM_BUY сюда намеренно НЕ входит — покупка stateless.
    private static final Set<String> FLOW_OWNED_ACTIONS = Set.of(
            CallbackData.CONFIRM_CREDIT,
            CallbackData.CONFIRM_ADD_ITEM,
            CallbackData.CONFIRM_ADD_EARN_REASON,
            CallbackData.CONFIRM_REFUND,
            CallbackData.CANCEL
    );

    private final UserService userService;
    private final ItemService itemService;
    private final EarnReasonService earnReasonService;
    private final PointsService pointsService;
    private final ConversationManager conversationManager;
    private final TelegramSender sender;
    private final MemberNotifier memberNotifier;

    public void route(CallbackQuery callbackQuery) {
        var chatId = callbackQuery.message().chat().id();
        var messageId = callbackQuery.message().messageId();
        var telegramUserId = callbackQuery.from().id();
        var data = callbackQuery.data();
        var action = CallbackData.action(data);

        sender.answerCallback(callbackQuery.id());

        if (conversationManager.hasActive(chatId) && FLOW_OWNED_ACTIONS.contains(action)) {
            conversationManager.get(chatId).ifPresent(flow -> flow.onCallback(data));
            return;
        }

        switch (action) {
            case CallbackData.ITEM_SELECT ->
                    showItemCard(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.CONFIRM_BUY ->
                    handleBuy(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.BACK_TO_CATALOG -> showCatalog(chatId, messageId);
            case CallbackData.RESTOCK_START ->
                    startRestock(chatId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.DEACTIVATE_ITEM ->
                    confirmDeactivate(chatId, messageId, CallbackData.paramAsLong(data));
            case CallbackData.CONFIRM_DEACTIVATE ->
                    handleDeactivate(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.ADD_EARN_REASON_START -> startAddEarnReason(chatId, telegramUserId);
            case CallbackData.REFUND_START ->
                    startRefund(chatId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.EARN_REASON_SELECT ->
                    showEarnReasonCard(chatId, messageId, CallbackData.paramAsLong(data));
            case CallbackData.BACK_TO_EARN_REASONS -> showEarnReasonsList(chatId, messageId);
            case CallbackData.EDIT_REASON_DESCRIPTION ->
                    startEditReason(chatId, telegramUserId, CallbackData.paramAsLong(data), EditEarnReasonFlow.Field.DESCRIPTION);
            case CallbackData.EDIT_REASON_POINTS ->
                    startEditReason(chatId, telegramUserId, CallbackData.paramAsLong(data), EditEarnReasonFlow.Field.POINTS);
            case CallbackData.DELETE_EARN_REASON ->
                    confirmDeleteReason(chatId, messageId, CallbackData.paramAsLong(data));
            case CallbackData.CONFIRM_DELETE_EARN_REASON ->
                    handleDeleteReason(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data));
            case CallbackData.HISTORY_PAGE ->
                    showPointsHistoryPage(chatId, messageId, telegramUserId, CallbackData.paramAsLong(data).intValue());
            default -> sender.send(chatId, "Эта кнопка больше не активна.");
        }
    }

    // --- Каталог (общий список) ---

    private void showCatalog(Long chatId, Integer messageId) {
        var items = itemService.listAvailable();
        if (items.isEmpty()) {
            sender.edit(chatId, messageId, "Пока нет доступных товаров 🤷", null);
            return;
        }
        sender.edit(chatId, messageId, "Доступные товары:", CatalogKeyboards.catalogList(items));
    }

    // --- Карточка товара: разная для member (купить) и admin (пополнить/деактивировать) ---

    private void showItemCard(Long chatId, Integer messageId, Long telegramUserId, Long itemId) {
        var user = userService.getByTelegramId(telegramUserId);
        var item = itemService.getById(itemId);

        if (user.isAdmin()) {
            showAdminItemCard(chatId, messageId, item);
        } else {
            showMemberItemCard(chatId, messageId, telegramUserId, item);
        }
    }

    private void showMemberItemCard(Long chatId, Integer messageId, Long telegramUserId, Item item) {
        int balance = pointsService.getBalance(telegramUserId);
        var text = "%s\n%s\nЦена: %d баллов\nВ наличии: %d шт.\nТвой баланс: %d баллов".formatted(
                item.getTitle(), item.getDescription() == null ? "" : item.getDescription(),
                item.getPrice(), item.getQuantity(), balance);

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Купить")
                        .callbackData(CallbackData.of(CallbackData.CONFIRM_BUY, item.getId())),
                new InlineKeyboardButton("⬅️ Назад к списку")
                        .callbackData(CallbackData.of(CallbackData.BACK_TO_CATALOG))
        );
        sender.edit(chatId, messageId, text, keyboard);
    }

    private void showAdminItemCard(Long chatId, Integer messageId, Item item) {
        var text = "%s\n%s\nЦена: %d баллов | Остаток: %d шт.".formatted(
                item.getTitle(), item.getDescription() == null ? "" : item.getDescription(),
                item.getPrice(), item.getQuantity());

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("\uD83D\uDD04 Пополнить")
                        .callbackData(CallbackData.of(CallbackData.RESTOCK_START, item.getId())),
                new InlineKeyboardButton("\uD83D\uDEAB Деактивировать")
                        .callbackData(CallbackData.of(CallbackData.DEACTIVATE_ITEM, item.getId())),
                new InlineKeyboardButton("⬅️ Назад")
                        .callbackData(CallbackData.of(CallbackData.BACK_TO_CATALOG))
        );
        sender.edit(chatId, messageId, text, keyboard);
    }

    // --- Покупка (member), stateless ---

    private void handleBuy(Long chatId, Integer messageId, Long telegramUserId, Long itemId) {
        try {
            var purchase = pointsService.buy(telegramUserId, itemId);
            int freshBalance = pointsService.getBalance(telegramUserId);
            sender.edit(chatId, messageId, "✅ Готово! Ты купила «%s» за %d баллов.\nОстаток баллов: %d"
                    .formatted(purchase.getItem().getTitle(), purchase.getPrice(), freshBalance), null);
        } catch (InsufficientBalanceException e) {
            sender.edit(chatId, messageId, "❌ Недостаточно баллов для этой покупки.", null);
        } catch (OutOfStockException e) {
            sender.edit(chatId, messageId, "❌ Этот товар только что закончился. Загляни в каталог ещё раз.", null);
        }
    }

    // --- Пополнение остатка (admin) ---

    private void startRestock(Long chatId, Long telegramUserId, Long itemId) {
        var item = itemService.getById(itemId);
        conversationManager.start(chatId, new RestockFlow(
                chatId, telegramUserId, itemId, item.getTitle(), sender, itemService, conversationManager));
    }

    // --- Деактивация товара (admin): подтверждение в два клика, без FSM ---

    private void confirmDeactivate(Long chatId, Integer messageId, Long itemId) {
        var item = itemService.getById(itemId);
        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Да")
                        .callbackData(CallbackData.of(CallbackData.CONFIRM_DEACTIVATE, itemId)),
                new InlineKeyboardButton("❌ Нет")
                        .callbackData(CallbackData.of(CallbackData.BACK_TO_CATALOG))
        );
        sender.edit(chatId, messageId,
                "Деактивировать «%s»? Товар исчезнет из каталога.".formatted(item.getTitle()), keyboard);
    }

    private void handleDeactivate(Long chatId, Integer messageId, Long telegramUserId, Long itemId) {
        itemService.deactivate(telegramUserId, itemId);
        sender.edit(chatId, messageId, "✅ Товар деактивирован.", null);
    }

    // --- Добавление способа заработка (admin) ---

    private void startAddEarnReason(Long chatId, Long telegramUserId) {
        // Триггер — inline-кнопка под списком способов заработка (MessageRouter),
        // а не reply-кнопка меню, поэтому flow стартует здесь, а не в MessageRouter.
        conversationManager.start(chatId, new AddEarnReasonFlow(
                chatId, telegramUserId, sender, earnReasonService, conversationManager, memberNotifier));
    }

    // --- Возврат покупки (admin) ---

    private void startRefund(Long chatId, Long telegramUserId, Long purchaseId) {
        var memberTelegramId = userService.getTheMember().getTelegramId();
        conversationManager.start(chatId, new RefundFlow(chatId, telegramUserId, purchaseId, sender, pointsService,
                conversationManager, memberNotifier, memberTelegramId));
    }

    private void showEarnReasonsList(Long chatId, Integer messageId) {
        var reasons = earnReasonService.listAvailable();
        sender.edit(chatId, messageId, EarnReasonKeyboards.adminListHeader(reasons),
                EarnReasonKeyboards.adminList(reasons));
    }

    private void showEarnReasonCard(Long chatId, Integer messageId, Long reasonId) {
        var reason = earnReasonService.getById(reasonId);
        var text = "%s\n%s\nБаллы: %s".formatted(
                reason.getTitle(),
                reason.getDescription() == null ? "—" : reason.getDescription(),
                reason.getPoints() == null ? "по договорённости" : reason.getPoints().toString());

        var keyboard = new InlineKeyboardMarkup();
        keyboard.addRow(new InlineKeyboardButton("✏️ Описание")
                .callbackData(CallbackData.of(CallbackData.EDIT_REASON_DESCRIPTION, reasonId)));
        keyboard.addRow(new InlineKeyboardButton("💰 Баллы")
                .callbackData(CallbackData.of(CallbackData.EDIT_REASON_POINTS, reasonId)));
        keyboard.addRow(new InlineKeyboardButton("🗑 Удалить")
                .callbackData(CallbackData.of(CallbackData.DELETE_EARN_REASON, reasonId)));
        keyboard.addRow(new InlineKeyboardButton("⬅️ Назад")
                .callbackData(CallbackData.of(CallbackData.BACK_TO_EARN_REASONS)));
        sender.edit(chatId, messageId, text, keyboard);
    }

    private void startEditReason(Long chatId, Long telegramUserId, Long reasonId, EditEarnReasonFlow.Field field) {
        var reason = earnReasonService.getById(reasonId);
        conversationManager.start(chatId, new EditEarnReasonFlow(
                chatId, telegramUserId, reasonId, reason.getTitle(), field, sender, earnReasonService, conversationManager));
    }

    private void confirmDeleteReason(Long chatId, Integer messageId, Long reasonId) {
        var reason = earnReasonService.getById(reasonId);
        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Да")
                        .callbackData(CallbackData.of(CallbackData.CONFIRM_DELETE_EARN_REASON, reasonId)),
                new InlineKeyboardButton("❌ Нет")
                        .callbackData(CallbackData.of(CallbackData.EARN_REASON_SELECT, reasonId))
        );
        sender.edit(chatId, messageId, "Удалить «%s»?".formatted(reason.getTitle()), keyboard);
    }

    private void handleDeleteReason(Long chatId, Integer messageId, Long telegramUserId, Long reasonId) {
        earnReasonService.deactivate(telegramUserId, reasonId);
        showEarnReasonsList(chatId, messageId); // сразу показываем обновлённый список
    }

    private void showPointsHistoryPage(Long chatId, Integer messageId, Long telegramUserId, int page) {
        var member = userService.getTheMember();
        var result = pointsService.getTransactionHistoryPage(
                telegramUserId, member.getTelegramId(), page, PointsHistoryView.PAGE_SIZE);
        sender.edit(chatId, messageId, PointsHistoryView.text(result), PointsHistoryView.keyboard(result));
    }
}
