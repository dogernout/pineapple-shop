package ru.dogernout.pineapple_shop.exception;

public class ItemNotActiveException extends DomainException {

    public ItemNotActiveException(Long itemId) {
        super("Item %d is not active".formatted(itemId));
    }
}
