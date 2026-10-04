package ru.dogernout.pineapple_shop.exception;

public class PurchaseAlreadyRefundedException extends DomainException {

    public PurchaseAlreadyRefundedException(Long purchaseId) {
        super("Purchase %d is already refunded".formatted(purchaseId));
    }
}
