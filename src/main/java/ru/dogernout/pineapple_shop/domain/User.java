package ru.dogernout.pineapple_shop.domain;

import ru.dogernout.pineapple_shop.domain.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "users", schema = "core", uniqueConstraints = @UniqueConstraint(columnNames = "telegram_id"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(name = "username", length = 64)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    // Материализованный баланс. Меняется ТОЛЬКО через атомарные bulk-update
    // запросы в UserRepository (creditBalance/debitBalance) — никогда через setter,
    // чтобы не потерять гарантии атомарности и не словить lost update.
    @Column(name = "balance", nullable = false)
    private Integer balance = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // для JPA
    }

    public User(Long telegramId, String username, Role role) {
        this.telegramId = telegramId;
        this.username = username;
        this.role = role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
