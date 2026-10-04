package ru.dogernout.pineapple_shop.exception;

public class AccessDeniedException extends DomainException {

    public AccessDeniedException(Long userId) {
        super("User %d is not allowed to perform this operation".formatted(userId));
    }
}
