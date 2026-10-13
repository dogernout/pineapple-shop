package ru.dogernout.pineapple_shop.bot.conversation.flow;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationFlow;
import ru.dogernout.pineapple_shop.bot.conversation.ConversationManager;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.bot.support.MemberNotifier;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.service.ItemService;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;

@RequiredArgsConstructor
public class AddItemFlow implements ConversationFlow {

    private enum Step { TITLE, DESCRIPTION, PRICE, QUANTITY, CONFIRM }

    private final Long chatId;
    private final Long adminTelegramId;
    private final TelegramSender sender;
    private final ItemService itemService;
    private final ConversationManager conversationManager;
    private final MemberNotifier notifier;

    private Step step = Step.TITLE;
    private String title;
    private String description;
    private Integer price;
    private Integer quantity;

    @Override
    public void onEnter() {
        sender.send(chatId, "Название товара?");
    }

    @Override
    public void onTextInput(String text) {
        switch (step) {
            case TITLE -> handleTitle(text);
            case DESCRIPTION -> handleDescription(text);
            case PRICE -> handlePrice(text);
            case QUANTITY -> handleQuantity(text);
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
        this.step = Step.PRICE;
        sender.send(chatId, "Цена в баллах?");
    }

    private void handlePrice(String text) {
        var parsed = parsePositiveInt(text);
        if (parsed == null) {
            sender.send(chatId, "Нужно ввести положительное целое число. Попробуй ещё раз.");
            return;
        }
        this.price = parsed;
        this.step = Step.QUANTITY;
        sender.send(chatId, "Сколько штук на складе?");
    }

    private void handleQuantity(String text) {
        var parsed = parseNonNegativeInt(text);
        if (parsed == null) {
            sender.send(chatId, "Нужно ввести целое число 0 или больше. Попробуй ещё раз.");
            return;
        }
        this.quantity = parsed;
        this.step = Step.CONFIRM;

        var summary = "%s\n%s\nЦена: %d баллов | Остаток: %d шт."
                .formatted(title, description == null ? "" : description, price, quantity);

        var keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Добавить").callbackData(CallbackData.of(CallbackData.CONFIRM_ADD_ITEM)),
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
        if (!CallbackData.CONFIRM_ADD_ITEM.equals(action) || step != Step.CONFIRM) {
            return;
        }

        try {
            itemService.addItem(adminTelegramId, title, description, price, quantity);
            sender.send(chatId, "✅ Товар «%s» добавлен в каталог.".formatted(title));
            notifier.newItemAdded(title, description, price, quantity);
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

    private Integer parseNonNegativeInt(String text) {
        try {
            int value = Integer.parseInt(text.strip());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
