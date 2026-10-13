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

    // Каталог (admin): управление конкретным товаром из карточки
    public static final String RESTOCK_START = "restock_start";
    public static final String DEACTIVATE_ITEM = "deactivate_item";
    public static final String CONFIRM_DEACTIVATE = "confirm_deactivate";

    // Начисление баллов (admin)
    public static final String CONFIRM_CREDIT = "confirm_credit";

    // Добавление товара (admin)
    public static final String CONFIRM_ADD_ITEM = "confirm_add_item";

    // Справочник "за что начисляют баллы" (admin)
    public static final String ADD_EARN_REASON_START = "add_earn_reason_start";
    public static final String CONFIRM_ADD_EARN_REASON = "confirm_add_earn_reason";

    // Редактирование справочника "за что начисляют баллы" (admin)
    public static final String EARN_REASON_SELECT = "earn_reason";
    public static final String BACK_TO_EARN_REASONS = "back_to_reasons";
    public static final String EDIT_REASON_DESCRIPTION = "edit_reason_desc";
    public static final String EDIT_REASON_POINTS = "edit_reason_points";
    public static final String DELETE_EARN_REASON = "delete_reason";
    public static final String CONFIRM_DELETE_EARN_REASON = "confirm_delete_reason";

    // Возврат покупки (admin)
    public static final String REFUND_START = "refund_start";
    public static final String CONFIRM_REFUND = "confirm_refund";

    // Общее для любого flow с подтверждением
    public static final String CANCEL = "cancel";

    // Постраничная история баллов (admin)
    public static final String HISTORY_PAGE = "tx_page";

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
