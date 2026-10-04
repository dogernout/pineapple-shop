package ru.dogernout.pineapple_shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "items", schema = "core")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false)
    private Integer price;

    // Остаток на складе. Меняется ТОЛЬКО через атомарные bulk-update запросы
    // в ItemRepository (decrementQuantity/incrementQuantity).
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Item() {
        // для JPA
    }

    public Item(String title, String description, Integer price, Integer quantity, User createdBy) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.createdBy = createdBy;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean isInStock() {
        return quantity != null && quantity > 0;
    }
}
