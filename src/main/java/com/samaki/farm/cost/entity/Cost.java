package com.samaki.farm.cost.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.farm.entity.Farm;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gharama moja ya uendeshaji - bili ya umeme, mafuta ya jenereta,
 * mshahara, dawa ya bwawa.
 *
 * =====================================================================
 * SHAMBA NI LAZIMA, MZUNGUKO NI WA HIARI - ndiyo umbo lote la darasa
 * hili.
 *
 * Kila gharama inatokea shamba fulani, hivyo {@code farm} ni NOT NULL
 * (V1 haikuwa nayo kabisa - angalia V23). Lakini si kila gharama ni ya
 * mzunguko fulani, na hiyo si hitilafu ya data - ni jinsi shamba
 * linavyotumia fedha:
 *
 *   * {@code cycle == null} -> gharama ya SHAMBA ZIMA. Umeme wa mwezi
 *     mzima unaendesha matanki yote; mshahara wa mlinzi unalinda shamba,
 *     si bwawa moja. Kuvilazimisha kwenye mzunguko mmoja kungepandisha
 *     gharama yake kwa kiasi ambacho si chake, na mizunguko mingine
 *     ingeonekana nafuu kuliko ilivyo.
 *   * {@code cycle != null} -> gharama ya MZUNGUKO HUO. Dawa
 *     iliyotumika kwenye bwawa hili, usafiri wa kupeleka mavuno haya.
 *
 * Mzunguko ukiwekwa, LAZIMA uwe wa shamba lililotajwa - ukaguzi uko
 * CostService.createCost, si kwenye kikwazo cha database, kwa sababu
 * `cycles` haina farm_id (shamba lake linafikiwa kupitia
 * production_units).
 *
 * HAIHUSIANI NA GHARAMA YA CHAKULA. `feed_purchases` ina fedha yake
 * (farm_id, unit_cost, total_cost) na `feeding_logs` inaunganisha
 * chakula na mzunguko kwa KILO pekee - hakuna safu ya fedha huko, wala
 * cycle_id kwenye manunuzi. Daftari hili ni NYONGEZA juu ya hilo, si
 * nakala yake (angalia V23 kwa hatari inayobaki, ambayo ni ya mtumiaji
 * kuunda aina iitwayo "Chakula").
 *
 * FARM/CYCLE/CATEGORY NI EAGER (chaguo-msingi la @ManyToOne) kwa
 * makusudi: kila mstari unaorudishwa unabeba shamba lake, mzunguko wake
 * na aina yake kwenye GraphQL - hakuna njia ya kusoma daftari hili bila
 * lebo hizo.
 * =====================================================================
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "costs")
@Data
@EqualsAndHashCode(callSuper = false, of = "costId")
@ToString(exclude = {"farm", "cycle", "costCategory"})
public class Cost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cost_id")
    private Integer costId;

    @ManyToOne
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    /** NULL = gharama ya shamba zima. Angalia javadoc ya darasa. */
    @ManyToOne
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;

    @ManyToOne
    @JoinColumn(name = "cost_category_id", nullable = false)
    private CostCategory costCategory;

    /** NUMERIC(14,2), na CHECK (amount > 0) tangu V23. */
    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    /** "LUKU ya Machi", "matengenezo ya pampu" - hiari kabisa. */
    @Column(columnDefinition = "text")
    private String description;
}
