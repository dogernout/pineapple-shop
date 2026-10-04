package ru.dogernout.pineapple_shop.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.dogernout.pineapple_shop.bot.router.CallbackRouter;
import ru.dogernout.pineapple_shop.bot.router.MessageRouter;
import ru.dogernout.pineapple_shop.bot.support.TelegramSender;
import ru.dogernout.pineapple_shop.exception.AccessDeniedException;
import ru.dogernout.pineapple_shop.exception.DomainException;
import ru.dogernout.pineapple_shop.exception.EntityNotFoundException;
import ru.dogernout.pineapple_shop.exception.InsufficientBalanceException;
import ru.dogernout.pineapple_shop.exception.ItemNotActiveException;
import ru.dogernout.pineapple_shop.exception.OutOfStockException;
import ru.dogernout.pineapple_shop.exception.PurchaseAlreadyRefundedException;
import com.pengrad.telegrambot.model.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Единственное место, которое знает и про MessageRouter, и про CallbackRouter.
 * Ловит DomainException централизованно — ни одному роутеру/flow не нужно
 * самому заботиться о тексте ошибки для пользователя.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PineappleShopDispatcher {

    private final MessageRouter messageRouter;
    private final CallbackRouter callbackRouter;
    private final TelegramSender sender;

    public void dispatch(Update update) {
        var chatId = extractChatId(update);
        try {
            if (update.message() != null) {
                messageRouter.route(update.message());
            } else if (update.callbackQuery() != null) {
                callbackRouter.route(update.callbackQuery());
            }
        } catch (DomainException e) {
            if (chatId != null) {
                sender.send(chatId, "⚠️ " + humanize(e));
            }
        } catch (Exception e) {
            log.error("Unhandled error while processing update {}", update.updateId(), e);
            if (chatId != null) {
                sender.send(chatId, "Что-то пошло не так, попробуй ещё раз.");
            }
        }
    }

    private Long extractChatId(Update update) {
        if (update.message() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null) {
            return update.callbackQuery().message().chat().id();
        }
        return null;
    }

    private String humanize(DomainException e) {
        return switch (e) {
            case AccessDeniedException ignored -> "Эта команда доступна только администратору.";
            case InsufficientBalanceException ignored -> "Недостаточно баллов.";
            case OutOfStockException ignored -> "Товар закончился.";
            case ItemNotActiveException ignored -> "Этот товар сейчас недоступен.";
            case PurchaseAlreadyRefundedException ignored -> "Эта покупка уже была возвращена.";
            case EntityNotFoundException ignored -> "Не нашёл то, что ты имел в виду.";
            default -> "Не получилось выполнить операцию.";
        };
    }
}
