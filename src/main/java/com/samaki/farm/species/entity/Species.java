package com.samaki.farm.species.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "species")
@Data
@EqualsAndHashCode(callSuper = false)
public class Species extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "species_id")
    private Integer speciesId;

    @Column(unique = true, nullable = false)
    private String name; // Sato / Kambale

    @Column(name = "growth_months_avg")
    private BigDecimal growthMonthsAvg;

    @Column(name = "avg_harvest_weight_kg")
    private BigDecimal avgHarvestWeightKg;
}
