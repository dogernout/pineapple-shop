package ru.dogernout.pineapple_shop.service;

import ru.dogernout.pineapple_shop.domain.enums.TransactionType;

import java.time.Instant;
import java.util.List;

public record TransactionHistoryPage(List<Entry> entries, int page, boolean hasNext, int balance) {

    public record Entry(Instant createdAt, TransactionType type, int amount, String comment, String itemTitle) {}
}
