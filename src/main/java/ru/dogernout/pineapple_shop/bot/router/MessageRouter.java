package ru.dogernout.pineapple_shop.bot.router;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.conversation.flow.CreditPointsFlow;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.config.PineappleShopProperties;
import ru.dogernout.pineapple_shop.domain.Item;
import ru.dogernout.pineapple_shop.domain.User;
import ru.dogernout.pineapple_shop.domain.enums.PurchaseStatus;
import ru.dogernout.pineapple_shop.domain.enums.Role;
import ru.dogernout.pineapple_shop.service.EarnReasonService;
import ru.dogernout.pineapple_shop.service.ItemService;
import ru.dogernout.pineapple_shop.service.PointsService;
import ru.dogernout.pineapple_shop.service.UserService;
import com.pengrad.telegrambot.model.Message;
import java.util.List;
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
            case BTN_EARN_REASONS, BTN_EARN_REASONS_ADMIN -> handleEarnReasons(chatId);
            case BTN_CATALOG -> handleCatalog(chatId);
            case BTN_CREDIT -> handleCreditStart(chatId, user);
            case BTN_PURCHASE_HISTORY -> handlePurchaseHistory(chatId, user);
            case BTN_ADD_ITEM ->
                    sender.send(chatId, "Этот сценарий добавим следующим шагом — по образцу начисления баллов.");
            default -> sender.send(chatId, "Не понимаю эту команду — воспользуйся меню ниже.");
        }
    }

    private void handleStart(Long chatId, Long telegramUserId, String username) {
        var role = telegramUserId.equals(properties.getAdminTelegramId()) ? Role.ADMIN : Role.MEMBER;
        var user = userService.registerIfAbsent(telegramUserId, username, role);
        sender.send(chatId, "Привет! 👋", forRole(user.getRole()));
    }

    private void handleBalance(Long chatId, Long telegramUserId) {
        int balance = pointsService.getBalance(telegramUserId);
        sender.send(chatId, "Твой баланс: 🎯 %d баллов".formatted(balance));
    }

    private void handleEarnReasons(Long chatId) {
        var reasons = earnReasonService.listAvailable();
        if (reasons.isEmpty()) {
            sender.send(chatId, "Пока нет активных способов заработать баллы 🤷");
            return;
        }
        var text = reasons.stream()
                .map(r -> r.getPoints() == null
                        ? "• %s — по договорённости".formatted(r.getTitle())
                        : "• %s — %d баллов".formatted(r.getTitle(), r.getPoints()))
                .collect(Collectors.joining("\n"));
        sender.send(chatId, "Вот за что можно получить баллы:\n\n" + text);
    }

    private void handleCatalog(Long chatId) {
        List<Item> items = itemService.listAvailable();
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
        var text = purchases.stream()
                .map(p -> "%s — %d баллов — %s %s".formatted(
                        p.getItem().getTitle(), p.getPrice(), p.getPurchasedAt(),
                        p.getStatus() == PurchaseStatus.REFUNDED
                                ? "↩️ возвращено" : "✅"))
                .collect(Collectors.joining("\n"));
        sender.send(chatId, text);
    }
}
