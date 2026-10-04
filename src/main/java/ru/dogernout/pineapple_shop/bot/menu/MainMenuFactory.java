package ru.dogernout.pineapple_shop.bot.menu;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.dogernout.pineapple_shop.domain.enums.Role;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MainMenuFactory {

    public static final String BTN_BALANCE = "💰 Баланс";
    public static final String BTN_EARN_REASONS = "🎯 Как заработать";
    public static final String BTN_CATALOG = "🛍 Каталог товаров";

    public static final String BTN_CREDIT = "💸 Начислить";
    public static final String BTN_ADD_ITEM = "➕ Добавить товар";
    public static final String BTN_EARN_REASONS_ADMIN = "🎯 Способы заработка";
    public static final String BTN_PURCHASE_HISTORY = "📜 История покупок";

    public static ReplyKeyboardMarkup forRole(Role role) {
        return role == Role.ADMIN ? adminMenu() : memberMenu();
    }

    private static ReplyKeyboardMarkup memberMenu() {
        KeyboardButton[] row1 = {new KeyboardButton(BTN_BALANCE), new KeyboardButton(BTN_EARN_REASONS)};
        KeyboardButton[] row2 = {new KeyboardButton(BTN_CATALOG)};
        return new ReplyKeyboardMarkup(row1, row2).resizeKeyboard(true);
    }

    private static ReplyKeyboardMarkup adminMenu() {
        KeyboardButton[] row1 = {new KeyboardButton(BTN_CREDIT), new KeyboardButton(BTN_CATALOG)};
        KeyboardButton[] row2 = {new KeyboardButton(BTN_ADD_ITEM), new KeyboardButton(BTN_EARN_REASONS_ADMIN)};
        KeyboardButton[] row3 = {new KeyboardButton(BTN_PURCHASE_HISTORY)};
        return new ReplyKeyboardMarkup(row1, row2, row3).resizeKeyboard(true);
    }
}
