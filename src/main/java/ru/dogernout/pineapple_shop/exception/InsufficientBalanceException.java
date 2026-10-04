package ru.dogernout.pineapple_shop.exception;

public class InsufficientBalanceException extends DomainException {

    public InsufficientBalanceException(Long userId, int requiredAmount) {
        super("User %d has insufficient balance for amount %d".formatted(userId, requiredAmount));
    }
}
