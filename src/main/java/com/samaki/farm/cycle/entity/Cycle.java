package com.samaki.farm.cycle.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.species.entity.Species;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "cycles")
@Data
@EqualsAndHashCode(callSuper = false)
public class Cycle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cycle_id")
    private Integer cycleId;

    @ManyToOne
    @JoinColumn(name = "unit_id")
    private ProductionUnit unit;

    @ManyToOne
    @JoinColumn(name = "species_id")
    private Species species;

    @Column(name = "stocking_date")
    private LocalDate stockingDate;

    @Column(name = "fingerlings_count")
    private Integer fingerlingsCount;

    @Column(name = "survival_rate_estimate")
    private BigDecimal survivalRateEstimate = new BigDecimal("0.85");

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate; // Kiotomatiki: stockingDate + species.growthMonthsAvg

    @Column(name = "actual_harvest_date")
    private LocalDate actualHarvestDate;

    private String status = "ACTIVE"; // ACTIVE / HARVESTED / FAILED
}
