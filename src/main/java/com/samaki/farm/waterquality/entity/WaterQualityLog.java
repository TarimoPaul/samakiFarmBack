package com.samaki.farm.waterquality.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "water_quality_logs")
@Data
@EqualsAndHashCode(callSuper = false, of = "logId")
@ToString(exclude = {"unit", "recordedBy"})
public class WaterQualityLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @ManyToOne
    @JoinColumn(name = "unit_id")
    private ProductionUnit unit;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    private BigDecimal ph;

    private BigDecimal temperature;

    private BigDecimal oxygen;

    private String notes;

    @ManyToOne
    @JoinColumn(name = "recorded_by_user_id")
    private FarmUser recordedBy;
}
