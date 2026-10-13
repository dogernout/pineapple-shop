package ru.dogernout.pineapple_shop.bot.router;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.dogernout.pineapple_shop.bot.support.CallbackData;
import ru.dogernout.pineapple_shop.domain.EarnReason;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EarnReasonKeyboards {

    static InlineKeyboardMarkup adminList(List<EarnReason> reasons) {
        var keyboard = new InlineKeyboardMarkup();
        for (var reason : reasons) {
            var label = reason.getPoints() == null
                    ? "%s — по договорённости".formatted(reason.getTitle())
                    : "%s — %d".formatted(reason.getTitle(), reason.getPoints());
            keyboard.addRow(new InlineKeyboardButton(label)
                    .callbackData(CallbackData.of(CallbackData.EARN_REASON_SELECT, reason.getId())));
        }
        keyboard.addRow(new InlineKeyboardButton("➕ Добавить способ")
                .callbackData(CallbackData.of(CallbackData.ADD_EARN_REASON_START)));
        return keyboard;
    }

    static String adminListHeader(List<EarnReason> reasons) {
        return reasons.isEmpty()
                ? "Пока нет способов заработать баллы 🤷"
                : "Способы заработка (нажми на пункт, чтобы изменить):";
    }
}