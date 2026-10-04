package ru.dogernout.pineapple_shop.domain;

import ru.dogernout.pineapple_shop.domain.enums.PurchaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "purchases", schema = "core")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    // Снапшот цены на момент покупки — не ссылаемся на item.price,
    // т.к. цена вещи может измениться позже.
    @Column(name = "price", nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PurchaseStatus status = PurchaseStatus.COMPLETED;

    @Column(name = "purchased_at", nullable = false, updatable = false)
    private Instant purchasedAt = Instant.now();

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private User refundedBy;

    @Column(name = "refund_reason")
    private String refundReason;

    protected Purchase() {
        // для JPA
    }

    public Purchase(User user, Item item, Integer price) {
        this.user = user;
        this.item = item;
        this.price = price;
    }

    public void markRefunded(User admin, String reason) {
        if (this.status != PurchaseStatus.COMPLETED) {
            throw new IllegalStateException("Purchase %d is not in COMPLETED state".formatted(id));
        }
        this.status = PurchaseStatus.REFUNDED;
        this.refundedAt = Instant.now();
        this.refundedBy = admin;
        this.refundReason = reason;
    }

}
