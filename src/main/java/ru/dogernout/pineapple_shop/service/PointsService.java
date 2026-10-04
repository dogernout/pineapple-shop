package ru.dogernout.pineapple_shop.service;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.domain.Item;
import ru.dogernout.pineapple_shop.domain.PointTransaction;
import ru.dogernout.pineapple_shop.domain.Purchase;
import ru.dogernout.pineapple_shop.domain.User;
import ru.dogernout.pineapple_shop.domain.enums.PurchaseStatus;
import ru.dogernout.pineapple_shop.domain.enums.TransactionType;
import ru.dogernout.pineapple_shop.exception.EntityNotFoundException;
import ru.dogernout.pineapple_shop.exception.InsufficientBalanceException;
import ru.dogernout.pineapple_shop.exception.ItemNotActiveException;
import ru.dogernout.pineapple_shop.exception.OutOfStockException;
import ru.dogernout.pineapple_shop.exception.PurchaseAlreadyRefundedException;
import ru.dogernout.pineapple_shop.repository.ItemRepository;
import ru.dogernout.pineapple_shop.repository.PointTransactionRepository;
import ru.dogernout.pineapple_shop.repository.PurchaseRepository;
import ru.dogernout.pineapple_shop.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ядро бизнес-логики баллов. Каждый публичный метод — одна бизнес-операция,
 * выполняющаяся в одной БД-транзакции целиком (всё или ничего).
 * <p>
 * Баланс пользователя и остаток товара меняются ТОЛЬКО через атомарные
 * bulk-update запросы репозиториев (UserRepository.creditBalance/debitBalance,
 * ItemRepository.increment/decrementQuantity). Их WHERE-условие само проверяет
 * достаточность средств/остатка — отдельный SELECT ... FOR UPDATE не нужен.
 * <p>
 * Важный нюанс JPA: bulk-update запросы идут в обход persistence context,
 * поэтому если бы где-то в рамках той же транзакции уже была прочитана (и
 * закэширована в контексте) сущность User/Item, её поля balance/quantity
 * в памяти устарели бы после такого UPDATE. В методах ниже мы читаем
 * balance/quantity только из свежих bulk-запросов (по числу affected rows),
 * а не через getBalance()/getQuantity() у ранее загруженной сущности — это
 * специально, не трогайте порядок без необходимости.
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final PurchaseRepository purchaseRepository;
    private final PointTransactionRepository transactionRepository;
    private final UserService userService;

    /**
     * Начисление баллов пользователю 2. Инициатор обязан быть ADMIN.
     */
    @Transactional
    public PointTransaction credit(Long adminTelegramId, Long memberTelegramId, int amount, String comment) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        var admin = userService.requireAdmin(adminTelegramId);
        var member = userService.getByTelegramId(memberTelegramId);

        userRepository.creditBalance(member.getId(), amount);

        var tx = new PointTransaction(member, amount, TransactionType.CREDIT, null, admin, comment);
        return transactionRepository.save(tx);
    }

    /**
     * Покупка вещи за баллы. Атомарно списывает баланс и остаток товара:
     * если остатка не хватило уже ПОСЛЕ списания баланса — вся транзакция
     * откатывается целиком (в т.ч. списанный баланс), т.к. исключение
     * прокидывается из @Transactional метода.
     */
    @Transactional
    public Purchase buy(Long memberTelegramId, Long itemId) {
        var member = userService.getByTelegramId(memberTelegramId);

        var item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Item", itemId));
        if (!item.isActive()) {
            throw new ItemNotActiveException(itemId);
        }

        int price = item.getPrice();

        int balanceUpdated = userRepository.debitBalance(member.getId(), price);
        if (balanceUpdated == 0) {
            throw new InsufficientBalanceException(member.getId(), price);
        }

        int stockUpdated = itemRepository.decrementQuantity(itemId);
        if (stockUpdated == 0) {
            throw new OutOfStockException(itemId);
        }

        var purchase = new Purchase(member, item, price);
        purchase = purchaseRepository.save(purchase);

        var tx = new PointTransaction(member, -price, TransactionType.DEBIT, purchase, member, null);
        transactionRepository.save(tx);

        return purchase;
    }

    /**
     * Возврат покупки: баланс и остаток товара восстанавливаются, покупка
     * помечается REFUNDED. Инициатор обязан быть ADMIN.
     */
    @Transactional
    public Purchase refund(Long adminTelegramId, Long purchaseId, String reason) {
        var admin = userService.requireAdmin(adminTelegramId);

        var purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", purchaseId));

        if (purchase.getStatus() != PurchaseStatus.COMPLETED) {
            throw new PurchaseAlreadyRefundedException(purchaseId);
        }

        // Обычное изменение управляемой сущности — пойдёт через dirty checking,
        // это НЕ bulk-update, поэтому здесь порядок с другими операциями не важен.
        purchase.markRefunded(admin, reason);

        userRepository.creditBalance(purchase.getUser().getId(), purchase.getPrice());
        itemRepository.incrementQuantity(purchase.getItem().getId());

        var tx = new PointTransaction(
                purchase.getUser(), purchase.getPrice(), TransactionType.REFUND, purchase, admin, reason);
        transactionRepository.save(tx);

        return purchase;
    }

    @Transactional(readOnly = true)
    public int getBalance(Long telegramId) {
        return userService.getByTelegramId(telegramId).getBalance();
    }

    @Transactional(readOnly = true)
    public List<PointTransaction> getTransactionHistory(Long telegramId) {
        var user = userService.getByTelegramId(telegramId);
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public Purchase getPurchase(Long purchaseId) {
        return purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase", purchaseId));
    }

    @Transactional(readOnly = true)
    public List<Purchase> getPurchaseHistory(Long telegramId) {
        var user = userService.getByTelegramId(telegramId);
        return purchaseRepository.findByUserIdOrderByPurchasedAtDesc(user.getId());
    }
}
