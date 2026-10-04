package ru.dogernout.pineapple_shop.bot.router;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.conversation.flow.AddItemFlow;
import ru.dogernout.pineapple_shop.bot.conversation.flow.CreditPointsFlow;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.config.PineappleShopProperties;
import ru.dogernout.pineapple_shop.domain.Purchase;
import ru.dogernout.pineapple_shop.domain.User;
import ru.dogernout.pineapple_shop.domain.enums.PurchaseStatus;
import ru.dogernout.pineapple_shop.domain.enums.Role;
import ru.dogernout.pineapple_shop.service.EarnReasonService;
import ru.dogernout.pineapple_shop.service.ItemService;
import ru.dogernout.pineapple_shop.service.PointsService;
import ru.dogernout.pineapple_shop.service.UserService;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_ADD_ITEM;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_BALANCE;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_CATALOG;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_CREDIT;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_EARN_REASONS;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_EARN_REASONS_ADMIN;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.BTN_PURCHASE_HISTORY;
import static ru.dogernout.pineapple_shop.bot.menu.MainMenuFactory.forRole;

@Component
@RequiredArgsConstructor
public class MessageRouter {

    private static final String TERMS_TEXT = """
        Привет! 👋

        При продолжении действий ты соглашаешься с правилами начисления и списания баллов. \
        При отсутствии активности каждые 7 дней будут списываться по 100 баллов вплоть до обнуления счёта.

        Чтобы внести предложения или оставить обратную связь, нужно написать личное сообщение \
        пользователю @dogernout.""";

    private final UserService userService;
    private final PointsService pointsService;
    private final ItemService itemService;
    private final EarnReasonService earnReasonService;
    private final ConversationManager conversationManager;
    private final TelegramSender sender;
    private final PineappleShopProperties properties;

    public void route(Message message) {
        var text = message.text();
        if (text == null) {
            return; // не текстовое сообщение (стикер, фото и т.п.) — на этом уровне игнорируем
        }
        text = text.strip();

        var chatId = message.chat().id();
        var telegramUserId = message.from().id();

        if ("/start".equals(text)) {
            handleStart(chatId, telegramUserId, message.from().username());
            return;
        }

        // Если у chatId открыт сценарий — текст уходит в него, а не в меню.
        if (conversationManager.hasActive(chatId)) {
            var finalText = text;
            conversationManager.get(chatId).ifPresent(flow -> flow.onTextInput(finalText));
            return;
        }

        var user = userService.getByTelegramId(telegramUserId);

        switch (text) {
            case BTN_BALANCE -> handleBalance(chatId, telegramUserId);
            case BTN_EARN_REASONS, BTN_EARN_REASONS_ADMIN ->
                    handleEarnReasons(chatId, user);
            case BTN_CATALOG -> handleCatalog(chatId);
            case BTN_CREDIT -> handleCreditStart(chatId, user);
            case BTN_ADD_ITEM -> handleAddItemStart(chatId, user);
            case BTN_PURCHASE_HISTORY -> handlePurchaseHistory(chatId, user);
            default -> sender.send(chatId, "Не понимаю эту команду — воспользуйся меню ниже.");
        }
    }

    private void handleStart(Long chatId, Long telegramUserId, String username) {
        var role = telegramUserId.equals(properties.getAdminTelegramId()) ? Role.ADMIN : Role.MEMBER;
        var user = userService.registerIfAbsent(telegramUserId, username, role);
        sender.send(chatId, TERMS_TEXT, forRole(user.getRole()));
    }

    private void handleBalance(Long chatId, Long telegramUserId) {
        int balance = pointsService.getBalance(telegramUserId);
        sender.send(chatId, "Твой баланс: 🎯 %d баллов".formatted(balance));
    }

    private void handleEarnReasons(Long chatId, User user) {
        var reasons = earnReasonService.listAvailable();

        var text = reasons.isEmpty()
                ?  "Пока нет активных способов заработать баллы 🤷"
                : "Вот за что можно получить баллы:\n\n" + reasons.stream()
                .map(r -> r.getPoints() == null
                        ? "• %s — по договорённости".formatted(r.getTitle())
                        : "• %s — %d баллов".formatted(r.getTitle(), r.getPoints()))
                .collect(Collectors.joining("\n"));

        if (!user.isAdmin()) {
            sender.send(chatId, text);
            return;
        }

        // Admin дополнительно видит кнопку добавления нового способа под списком.
        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("➕ Добавить способ")
                        .callbackData(CallbackData.of(CallbackData.ADD_EARN_REASON_START))
        );
        sender.send(chatId, text, keyboard);
    }

    private void handleCatalog(Long chatId) {
        var items = itemService.listAvailable();
        if (items.isEmpty()) {
            sender.send(chatId, "Пока нет доступных товаров 🤷");
            return;
        }
        sender.send(chatId, "Доступные товары:", CatalogKeyboards.catalogList(items));
    }

    private void handleCreditStart(Long chatId, User user) {
        if (!user.isAdmin()) {
            sender.send(chatId, "Эта команда доступна только администратору.");
            return;
        }
        var member = userService.getTheMember();
        conversationManager.start(chatId, new CreditPointsFlow(
                chatId, user.getTelegramId(), member.getTelegramId(), sender, pointsService, conversationManager));
    }

    private void handleAddItemStart(Long chatId, User user) {
        if (!user.isAdmin()) {
            sender.send(chatId, "Эта команда доступна только администратору.");
            return;
        }
        conversationManager.start(chatId, new AddItemFlow(
                chatId, user.getTelegramId(), sender, itemService, conversationManager));
    }

    private void handlePurchaseHistory(Long chatId, User user) {
        if (!user.isAdmin()) {
            sender.send(chatId, "Эта команда доступна только администратору.");
            return;
        }
        var member = userService.getTheMember();
        var purchases = pointsService.getPurchaseHistory(member.getTelegramId());
        if (purchases.isEmpty()) {
            sender.send(chatId, "Покупок пока не было.");
            return;
        }

        // Каждая покупка — отдельное сообщение, т.к. у COMPLETED есть своя кнопка
        // "Вернуть" с собственным purchaseId в callback_data.
        for (Purchase purchase : purchases) {
            var text = "%s — %d баллов — %s %s".formatted(
                    purchase.getItem().getTitle(), purchase.getPrice(), purchase.getPurchasedAt(),
                    purchase.getStatus() == PurchaseStatus.REFUNDED ? "↩️ возвращено" : "✅");

            if (purchase.getStatus() == PurchaseStatus.COMPLETED) {
                var keyboard = new InlineKeyboardMarkup(
                        new InlineKeyboardButton("↩️ Вернуть")
                                .callbackData(CallbackData.of(CallbackData.REFUND_START, purchase.getId()))
                );
                sender.send(chatId, text, keyboard);
            } else {
                sender.send(chatId, text);
            }
        }
    }
}
