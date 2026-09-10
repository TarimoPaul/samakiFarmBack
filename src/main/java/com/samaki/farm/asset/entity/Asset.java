package com.samaki.farm.asset.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farm.entity.Farm;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kitu kimoja kinachomilikiwa - generator, pikipiki, jengo, tanki.
 *
 * SHAMBA NI LAZIMA, na ndilo linalotofautisha daftari hili na katalogi
 * yake: aina ni ya kampuni nzima, KITU chenyewe kiko mahali fulani.
 * Bila farm_id, jumla ya "shamba hili limewekeza kiasi gani" - swali
 * ambalo frontend inaulizwa kulijibu - isingeweza kukokotolewa kabisa.
 *
 * LAKINI KUSOMA NI KWA KAMPUNI NZIMA. Mmiliki mwenye mashamba matatu
 * anaona mali zote kwenye orodha MOJA, kila mstari ukiwa na shamba lake
 * (angalia AssetService.listAssets). Hiyo ndiyo tofauti kubwa kati ya
 * daftari hili na data ya uzalishaji, ambayo huchujwa kwa shamba teule
 * la mwombaji kupitia PermissionChecker.requireFarmScope.
 *
 * FARM NI EAGER (chaguo-msingi la @ManyToOne) kwa makusudi: kila mstari
 * unaorudishwa UNABEBA shamba lake kwenye GraphQL - hakuna njia ya
 * kuisoma orodha hii bila mashamba yake.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "assets")
@Data
@EqualsAndHashCode(callSuper = false, of = "assetId")
@ToString(exclude = {"farm", "assetCategory"})
public class Asset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Integer assetId;

    @ManyToOne
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @ManyToOne
    @JoinColumn(name = "asset_category_id", nullable = false)
    private AssetCategory assetCategory;

    @Column(nullable = false, length = 150)
    private String name;

    /** Bei ya kununulia. NUMERIC(14,2), na CHECK (cost > 0) tangu V21. */
    @Column(nullable = false)
    private BigDecimal cost;

    /**
     * Lebo ya ukubwa - "5000L", "ekari 2", "20HP". Maandishi ya HIARI kwa
     * sababu vitu vya daftari hili havishiriki kipimo kimoja; safu ya
     * namba ingelazimisha lita, mita na farasi kwenye kizio kimoja.
     */
    @Column(name = "size_label", length = 80)
    private String sizeLabel;

    @Column(name = "acquired_date", nullable = false)
    private LocalDate acquiredDate;
}
