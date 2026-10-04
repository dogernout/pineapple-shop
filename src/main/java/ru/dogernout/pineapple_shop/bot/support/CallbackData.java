package ru.dogernout.pineapple_shop.bot.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * callback_data у Telegram — обычная строка до 64 байт. Договариваемся о формате
 * "action:param" (param опционален) и работаем с ним через этот класс, чтобы
 * формат менялся в одном месте.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallbackData {

    private static final String SEPARATOR = ":";

    // Каталог / покупка (member)
    public static final String ITEM_SELECT = "item";
    public static final String CONFIRM_BUY = "confirm_buy";
    public static final String BACK_TO_CATALOG = "back_to_catalog";

    // Начисление баллов (admin)
    public static final String CONFIRM_CREDIT = "confirm_credit";

    // Общее для любого flow с подтверждением
    public static final String CANCEL = "cancel";

    public static String of(String action) {
        return action;
    }

    public static String of(String action, Object param) {
        return action + SEPARATOR + param;
    }

    public static String action(String raw) {
        int idx = raw.indexOf(SEPARATOR);
        return idx == -1 ? raw : raw.substring(0, idx);
    }

    public static String param(String raw) {
        int idx = raw.indexOf(SEPARATOR);
        return idx == -1 ? null : raw.substring(idx + 1);
    }

    public static Long paramAsLong(String raw) {
        String param = param(raw);
        return param == null ? null : Long.valueOf(param);
    }
}
