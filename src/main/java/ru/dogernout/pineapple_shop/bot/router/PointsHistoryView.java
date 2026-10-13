package ru.dogernout.pineapple_shop.bot.router;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.service.TransactionHistoryPage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PointsHistoryView {

    static final int PAGE_SIZE = 10;

    // Подставь свою зону, если сервер живёт в другой: ZoneId.of("Europe/Moscow")
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    static String text(TransactionHistoryPage page) {
        if (page.entries().isEmpty()) {
            return "Операций с баллами пока не было.";
        }
        var lines = page.entries().stream()
                .map(PointsHistoryView::line)
                .collect(Collectors.joining("\n"));
        return "История баллов (стр. %d). Баланс: %d\n\n%s".formatted(page.page() + 1, page.balance(), lines);
    }

    private static String line(TransactionHistoryPage.Entry e) {
        var sign = e.amount() > 0 ? "+" + e.amount() : String.valueOf(e.amount());
        var what = switch (e.type()) {
            case CREDIT -> "➕ Начисление";
            case DEBIT -> "🛍 Покупка «%s»".formatted(e.itemTitle());
            case REFUND -> "↩️ Возврат «%s»".formatted(e.itemTitle());
        };
        var comment = e.comment() == null || e.comment().isBlank() ? "" : " · " + e.comment();
        return "%s  %s  %s%s".formatted(FORMAT.format(e.createdAt()), sign, what, comment);
    }

    /** null, если листать некуда (одна страница). */
    static InlineKeyboardMarkup keyboard(TransactionHistoryPage page) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (page.page() > 0) {
            buttons.add(new InlineKeyboardButton("⬅️ Новее")
                    .callbackData(CallbackData.of(CallbackData.HISTORY_PAGE, page.page() - 1)));
        }
        if (page.hasNext()) {
            buttons.add(new InlineKeyboardButton("Старше ➡️")
                    .callbackData(CallbackData.of(CallbackData.HISTORY_PAGE, page.page() + 1)));
        }
        if (buttons.isEmpty()) {
            return null;
        }
        var keyboard = new InlineKeyboardMarkup();
        keyboard.addRow(buttons.toArray(InlineKeyboardButton[]::new));
        return keyboard;
    }
}
