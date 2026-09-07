package com.samaki.farm.cycle.entity;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.generator.EventType;

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

    /**
     * Hali za mzunguko - ZILE ZILE za `cycles.status` (V1) na za
     * CycleService.STATUSES.
     *
     * Zipo hapa kama constants kwa sababu maandishi "ACTIVE" yalikuwa
     * yameandikwa kwa mkono kwenye module TATU (CycleService,
     * DailyTaskRepository, FeedService). Yakiandikwa mahali pengi, siku moja
     * mmoja atabadilika bila wenzake.
     */
    public static final String ACTIVE = "ACTIVE";
    public static final String HARVESTED = "HARVESTED";
    public static final String FAILED = "FAILED";

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

    /**
     * Umri wa samaki (miezi) SIKU WALIPOWEKWA - si sifuri kwa kila mzunguko.
     *
     * Mkulima anayenunua samaki wa miezi 2 hasubiri muda ule ule wa
     * anayeanza na vifaranga; ndiyo hesabu inayotoa expectedHarvestDate
     * (angalia CycleService.expectedHarvestDate). Sifuri - chaguo-msingi -
     * ndiyo hali ya zamani: wadogo kabisa.
     */
    @Column(name = "stocking_age_months", nullable = false)
    private Integer stockingAgeMonths = 0;

    @Column(name = "survival_rate_estimate")
    private BigDecimal survivalRateEstimate = new BigDecimal("0.85");

    // Kiotomatiki wakati wa kuweka: stockingDate + (growthMonthsAvg -
    // stockingAgeMonths). Ni UTABIRI unaosomwa; HAKUNA kitu chochote
    // kinachobadilisha hali ya mzunguko tarehe hii ikifika.
    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(name = "actual_harvest_date")
    private LocalDate actualHarvestDate;

    /** Samaki waliovunwa. NULL hadi mzunguko ufungwe (closeCycle). */
    @Column(name = "harvested_count")
    private Integer harvestedCount;

    /** Uzito wote wa mavuno (kg). NULL hadi mzunguko ufungwe. */
    @Column(name = "total_weight_kg")
    private BigDecimal totalWeightKg;

    /** Maelezo ya mvunaji - hiari kabisa. */
    @Column(name = "harvest_notes")
    private String harvestNotes;

    /**
     * Kiwango cha kuishi KILICHOTOKEA = harvestedCount / fingerlingsCount.
     *
     * HAIANDIKWI NA MTU YEYOTE, na hilo ndilo lengo. Ni GENERATED ALWAYS
     * ... STORED kwenye V19, hivyo Postgres yenyewe inakataa kila jaribio la
     * kuiandika - si service pekee inayoilinda. insertable/updatable=false
     * inazuia Hibernate kujaribu (ingekuwa kosa la SQL), na @Generated
     * inaifanya isomwe upya baada ya INSERT NA baada ya UPDATE, ili jibu la
     * closeCycle liwe nayo mara moja. Ni mtindo ule ule wa
     * FeedPurchase.totalCost.
     *
     * NI TOFAUTI KABISA na survivalRateEstimate hapo juu: ile ni MAKISIO
     * (0.85) yanayowekwa siku ya kuweka na kubaki yalivyo; hii ni
     * KILICHOTOKEA, kinachojulikana siku ya kuvuna pekee. Zote mbili
     * zinabaki, kwa sababu kulinganisha makisio na matokeo ndiyo maana ya
     * kuwa na makisio.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "actual_survival_rate", insertable = false, updatable = false)
    private BigDecimal actualSurvivalRate;

    private String status = ACTIVE; // ACTIVE / HARVESTED / FAILED
}
