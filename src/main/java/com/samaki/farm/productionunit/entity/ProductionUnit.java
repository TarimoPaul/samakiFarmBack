package com.samaki.farm.productionunit.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farm.entity.Farm;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "production_units")
@Data
@EqualsAndHashCode(callSuper = false)
public class ProductionUnit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_id")
    private Integer unitId;

    @ManyToOne
    @JoinColumn(name = "farm_id")
    private Farm farm;

    private String code; // mfano T1

    @Enumerated(EnumType.STRING)
    private UnitType type; // TANK / POND / BWAWA

    @Column(name = "size_m3")
    private BigDecimal sizeM3;

    @Column(name = "water_source")
    private String waterSource;

    private String status = "IDLE"; // IDLE / ACTIVE / MAINTENANCE

    public enum UnitType { TANK, POND, BWAWA }
}
