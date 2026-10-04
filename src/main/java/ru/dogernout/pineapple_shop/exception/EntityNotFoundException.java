package ru.dogernout.pineapple_shop.exception;

public class EntityNotFoundException extends DomainException {

    public EntityNotFoundException(String entityName, Long id) {
        super("%s %d not found".formatted(entityName, id));
    }
}
