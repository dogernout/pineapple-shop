package ru.dogernout.pineapple_shop.bot.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.dogernout.pineapple_shop.service.UserService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberNotifier {

    private final TelegramSender sender;
    private final UserService userService;

    public void pointsCredited(Long memberTelegramId, int amount, String comment, int balance) {
        var text = new StringBuilder("💸 Тебе начислено %d баллов".formatted(amount));
        if (comment != null && !comment.isBlank()) {
            text.append("\nЗа что: ").append(comment);
        }
        text.append("\nБаланс: ").append(balance);
        safeSend(memberTelegramId, text.toString());
    }

    public void purchaseRefunded(Long memberTelegramId, String itemTitle, int price, String reason, int balance) {
        var text = new StringBuilder("↩️ Покупка «%s» возвращена: +%d баллов".formatted(itemTitle, price));
        if (reason != null && !reason.isBlank()) {
            text.append("\nПричина: ").append(reason);
        }
        text.append("\nБаланс: ").append(balance);
        safeSend(memberTelegramId, text.toString());
    }

    public void newItemAdded(String title, String description, int price, int quantity) {
        // Товар с нулевым остатком купить нельзя, поэтому о нём пока не сообщаем
        if (quantity <= 0) {
            return;
        }
        var text = new StringBuilder("🆕 В каталоге новый товар: «%s»".formatted(title));
        if (description != null && !description.isBlank()) {
            text.append("\n").append(description);
        }
        text.append("\nЦена: %d баллов | В наличии: %d шт.".formatted(price, quantity));
        sendToMember(text.toString());
    }

    public void newEarnReasonAdded(String title, String description, Integer points) {
        var text = new StringBuilder("🎯 Новый способ заработать баллы: «%s» — %s".formatted(
                title, points == null ? "по договорённости" : points + " баллов"));
        if (description != null && !description.isBlank()) {
            text.append("\n").append(description);
        }
        sendToMember(text.toString());
    }

    private void sendToMember(String text) {
        userService.findTheMember().ifPresent(member -> safeSend(member.getTelegramId(), text));
    }

    // В личном чате chatId совпадает с telegram id пользователя.
    private void safeSend(Long chatId, String text) {
        try {
            sender.send(chatId, text);
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомление пользователю {}", chatId, e);
        }
    }
}
