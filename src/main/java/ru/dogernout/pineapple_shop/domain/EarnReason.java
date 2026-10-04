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

/**
 * Справочник "за что начисляются баллы". Чисто информационный, никак не связан
 * с point_transactions — начисление всегда делается вручную через PointsService.credit().
 */
@Getter
@Entity
@Table(name = "earn_reasons", schema = "core")
public class EarnReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "description")
    private String description;

    // Ориентировочное количество баллов, может быть null ("по договорённости")
    @Column(name = "points")
    private Integer points;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected EarnReason() {
        // для JPA
    }

    public EarnReason(String title, String description, Integer points, User createdBy) {
        this.title = title;
        this.description = description;
        this.points = points;
        this.createdBy = createdBy;
    }

    public void deactivate() {
        this.active = false;
    }

}
