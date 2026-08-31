package com.samaki.farm.waterquality.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.user.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kipimo kimoja cha ubora wa maji kwenye KITENGO (tanki/bwawa).
 *
 * Safu ni zile zile za V1 / ERD / Data Dictionary - hakuna iliyoongezwa:
 * unit_id, log_date, ph, temperature, oxygen, notes, recorded_by_user_id.
 *
 * KIMEFUNGWA KWENYE KITENGO, si mzunguko. Hivyo ndivyo schema ilivyo
 * (water_quality_logs.unit_id -> production_units), na ina maana: maji ni
 * mali ya tanki, si ya samaki walioko ndani yake kwa msimu huu. Kipimo
 * kinabaki kuwa cha kweli hata tanki likiwa tupu kati ya mizunguko.
 * Query ya "vipimo vya mzunguko huu" bado ipo - inapitia mzunguko hadi
 * kwenye kitengo chake (angalia WaterQualityService).
 *
 * Shamba HALIPO kwenye jedwali hili: linajulikana kupitia
 * unit -> farm, na hiyo ndiyo njia ambayo scoping inafuata - sawa na
 * feeding_logs inavyopitia cycle -> unit -> farm.
 */
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

    /** numeric(3,1) - kipimo cha 0.0 hadi 14.0. */
    private BigDecimal ph;

    /** numeric(4,1), nyuzi Selsiasi. */
    private BigDecimal temperature;

    /** numeric(4,1) - hewa ya oksijeni iliyoyeyuka (DO), mg/L. */
    private BigDecimal oxygen;

    /**
     * numeric(4,2) - amonia jumla (NH3 + NH4+), mg/L.
     *
     * Desimali MBILI, tofauti na vipimo vingine: 0.02 ni salama na 0.25
     * inaua polepole, hivyo desimali moja ingefuta tofauti inayoamua
     * (angalia V11__water_quality_ammonia.sql).
     */
    private BigDecimal ammonia;

    @Column(name = "notes")
    private String notes;

    @ManyToOne
    @JoinColumn(name = "recorded_by_user_id")
    // MTU aliyerekodi, si uanachama - recorded_by_user_id inaelekea `users`.
    private User recordedBy;
}
