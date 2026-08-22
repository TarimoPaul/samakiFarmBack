package com.samaki.farm.feed.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farm.entity.Farm;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "feed_purchases")
@Data
@EqualsAndHashCode(callSuper = false, of = "purchaseId")
@ToString(exclude = "farm")
public class FeedPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Integer purchaseId;

    @ManyToOne
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "feed_type", nullable = false)
    private String feedType;

    @Column(name = "quantity_kg", nullable = false)
    private BigDecimal quantityKg;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    // total_cost ni GENERATED ALWAYS AS (quantity_kg * unit_cost) STORED kwenye
    // V1 - database ndiyo inayoikokotoa. insertable/updatable=false inazuia
    // Hibernate kujaribu kuiandika (ingekuwa kosa la SQL), na @Generated
    // inaifanya isomwe upya baada ya INSERT ili jibu la GraphQL liwe nayo.
    @Generated(event = EventType.INSERT)
    @Column(name = "total_cost", insertable = false, updatable = false)
    private BigDecimal totalCost;

    private String supplier;
}
