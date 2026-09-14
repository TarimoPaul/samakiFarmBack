package com.samaki.farm.harvest.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.cycle.entity.Cycle;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tukio MOJA la samaki kutoka kwenye bwawa - mauzo ya leo, vifo vya
 * jana, uhamisho wa wiki iliyopita.
 *
 * =====================================================================
 * MAVUNO NI MATUKIO MENGI, SI NAMBA MOJA (angalia V25)
 *
 * Mzunguko mmoja una matukio mengi kwa wiki kadhaa. closeCycle
 * INAYAJUMLISHA - haipokei idadi wala uzito kutoka kwa mwombaji tena
 * (angalia HarvestTotals).
 *
 * SABABU TATU, HAKUNA NYINGINE - zile zilizotajwa na pilot:
 *
 *   * SOLD    - wameuzwa. weightKg na saleAmount ni LAZIMA (> 0).
 *   * DIED    - wamekufa. HAWAMO kwenye kiwango cha kuishi; wanaenda
 *               cycles.mortality_count.
 *   * REMOVED - wametolewa wakiwa hai bila kuuzwa (kuhamishwa, kuliwa
 *               nyumbani, kutolewa zawadi). WAMO kwenye kiwango cha kuishi
 *               - walitoka wakiwa hai.
 *
 * saleAmount ni ya SOLD PEKEE; kwa nyingine ni null kila mara (CHECK ya
 * V25 inalisema hilo pia). Ni FEDHA, hivyo GraphQL inairudisha null kwa
 * asiye na `view_finance` - angalia HarvestResolver.saleAmount.
 * =====================================================================
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "harvest_events")
@Data
@EqualsAndHashCode(callSuper = false, of = "harvestEventId")
@ToString(exclude = "cycle")
public class HarvestEvent extends BaseEntity {

    /**
     * Sababu za tukio - ZILE ZILE za CHECK chk_harvest_events_reason (V25).
     * Zikibadilika hapa, lazima zibadilike pale pia.
     */
    public enum Reason {
        SOLD, DIED, REMOVED;

        /** Samaki waliotoka WAKIWA HAI - ndio wanaohesabiwa kwenye kuishi. */
        public boolean leftAlive() {
            return this != DIED;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "harvest_event_id")
    private Integer harvestEventId;

    @ManyToOne
    @JoinColumn(name = "cycle_id", nullable = false)
    private Cycle cycle;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "fish_count", nullable = false)
    private Integer fishCount;

    /** LAZIMA kwa SOLD; hiari (null) kwa DIED/REMOVED. */
    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    /** Kwa SOLD pekee - null kwa DIED/REMOVED. */
    @Column(name = "sale_amount")
    private BigDecimal saleAmount;
}
