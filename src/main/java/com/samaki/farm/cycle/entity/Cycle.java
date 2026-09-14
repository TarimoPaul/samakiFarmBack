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

    /**
     * Gharama ya vifaranga siku ya kuweka. HIARI - NULL = haikurekodiwa.
     *
     * FEDHA: GraphQL inairudisha null kwa asiye na `view_finance` (angalia
     * CycleResolver.fingerlingCost). Entity yenyewe HAIGUSWI kwa ajili ya
     * kuficha - angalia FeedPurchaseView kwa kwa nini.
     */
    @Column(name = "fingerling_cost")
    private BigDecimal fingerlingCost;

    /**
     * Samaki WALIOTOKA WAKIWA HAI - jumla ya matukio ya SOLD + REMOVED
     * (V25). NULL hadi mzunguko ufungwe.
     *
     * HAIPOKELEWI kutoka kwa mwombaji tena: closeCycle inaijumlisha
     * kutoka harvest_events. Mizunguko iliyofungwa kabla ya V25 inabaki na
     * namba yao ya mkono (hakuna backfill).
     */
    @Column(name = "harvested_count")
    private Integer harvestedCount;

    /** Uzito wa SOLD + REMOVED (kg), kutoka matukio. NULL hadi mzunguko ufungwe. */
    @Column(name = "total_weight_kg")
    private BigDecimal totalWeightKg;

    /**
     * Vifo - jumla ya matukio ya DIED. NULL hadi mzunguko ufungwe.
     *
     * Kando ya harvestedCount KWA MAKUSUDI: samaki aliyekufa ametoka
     * bwawani lakini hakuishi, hivyo HAMO kwenye kiwango cha kuishi.
     */
    @Column(name = "mortality_count")
    private Integer mortalityCount;

    /**
     * Mapato - jumla ya sale_amount ya matukio ya SOLD. NULL hadi mzunguko
     * ufungwe; SIFURI kwa mzunguko uliofungwa bila mauzo.
     *
     * FEDHA: imefichwa kwa asiye na `view_finance`, kama fingerlingCost.
     */
    @Column(name = "total_revenue")
    private BigDecimal totalRevenue;

    /** Maelezo ya mvunaji - hiari kabisa. */
    @Column(name = "harvest_notes")
    private String harvestNotes;

    /**
     * Kiwango cha kuishi KILICHOTOKEA = harvestedCount / fingerlingsCount,
     * yaani (SOLD + REMOVED) / vifaranga. DIED haimo.
     *
     * HAIANDIKWI NA MTU YEYOTE, na hilo ndilo lengo. Tangu V25 hata
     * harvestedCount - kinachogawanywa - hakitoki kwa mwombaji: ni jumla
     * ya matukio inayokokotolewa na closeCycle. Ni GENERATED ALWAYS
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
