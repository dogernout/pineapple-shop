package ru.dogernout.pineapple_shop.domain;

import ru.dogernout.pineapple_shop.domain.enums.TransactionType;
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

/**
 * Универсальный журнал движения баллов — источник истины для любой сверки/аудита.
 * Создаётся исключительно из PointsService, никогда напрямую.
 */
@Getter
@Entity
@Table(name = "point_transactions", schema = "core")
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Знаковое значение: + начисление/возврат, - списание
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TransactionType type;

    // Заполнено только для DEBIT/REFUND
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PointTransaction() {
        // для JPA
    }

    public PointTransaction(User user, Integer amount, TransactionType type,
                            Purchase purchase, User initiatedBy, String comment) {
        this.user = user;
        this.amount = amount;
        this.type = type;
        this.purchase = purchase;
        this.initiatedBy = initiatedBy;
        this.comment = comment;
    }

}
