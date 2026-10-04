package ru.dogernout.pineapple_shop.exception;

/**
 * Базовый класс для доменных исключений — тех, что bot-слой должен ловить
 * и превращать в понятный пользователю текст, а не в stacktrace.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
