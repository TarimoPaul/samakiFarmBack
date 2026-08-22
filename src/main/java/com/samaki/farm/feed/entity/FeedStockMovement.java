package com.samaki.farm.feed.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farm.entity.Farm;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Leja ya stoo ya chakula. HAIANDIKWI moja kwa moja na mteja - inazalishwa
 * na FeedService: kununua kunaingiza IN, kulisha kunaingiza OUT. Salio ni
 * jumla ya IN kutoa jumla ya OUT (angalia FeedStockMovementRepository).
 *
 * Ni mtindo ule ule wa CycleService kuzalisha daily_tasks kiotomatiki -
 * mteja anaomba kitendo cha kibiashara, mfumo unaandika rekodi zinazoambatana.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "feed_stock_movements")
@Data
@EqualsAndHashCode(callSuper = false, of = "movementId")
@ToString(exclude = "farm")
public class FeedStockMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Integer movementId;

    @ManyToOne
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "quantity_kg", nullable = false)
    private BigDecimal quantityKg;

    @Column(name = "reference_purchase_id")
    private Integer referencePurchaseId;

    @Column(name = "reference_feeding_log_id")
    private Integer referenceFeedingLogId;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt = Instant.now();

    /** CHECK (direction IN ('IN','OUT')) kwenye V1. */
    public enum Direction { IN, OUT }
}
