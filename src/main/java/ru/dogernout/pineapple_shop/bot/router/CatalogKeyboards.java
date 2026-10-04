package ru.dogernout.pineapple_shop.bot.router;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.domain.Item;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import java.util.List;

/**
 * Список товаров показывается в двух местах (reply-кнопка "Каталог" и кнопка
 * "⬅️ Назад к списку" внутри карточки товара) — держим сборку клавиатуры в одном месте.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CatalogKeyboards {

    static InlineKeyboardMarkup catalogList(List<Item> items) {
        var keyboard = new InlineKeyboardMarkup();
        for (var item : items) {
            keyboard.addRow(new InlineKeyboardButton(
                    "%s — %d баллов (%d шт.)".formatted(item.getTitle(), item.getPrice(), item.getQuantity()))
                    .callbackData(CallbackData.of(CallbackData.ITEM_SELECT, item.getId())));
        }
        return keyboard;
    }
}
