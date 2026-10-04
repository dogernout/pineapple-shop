package ru.dogernout.pineapple_shop.exception;

public class OutOfStockException extends DomainException {

    public OutOfStockException(Long itemId) {
        super("Item %d is out of stock".formatted(itemId));
    }
}
