package com.samaki.farm.feed.entity;

import com.samaki.farm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

/**
 * Aina ya chakula - katalogi YA KIMFUMO, si ya shamba.
 *
 * HAINA farm_id kwa makusudi, kama `species` ya V1: "Pellet 3mm ni chakula
 * cha samaki wa miezi 2 hadi 5" ni ukweli wa chakula chenyewe, si uamuzi wa
 * shamba fulani. Mashamba yote yanasoma katalogi ile ile, na kuinakili kwa
 * kila shamba kungefanya aina ile ile iwe na madirisha tofauti ya umri
 * sehemu tofauti - hasa tatizo ambalo maandishi huru ya V1 yalikuwa nalo.
 *
 * Tofauti na Species, katalogi hii INAANDIKWA kupitia API
 * (FeedService.createFeedType, `manage_feed_stock`): aina mpya za chakula
 * zinaingia sokoni mara kwa mara, wakati aina za samaki hazibadiliki.
 *
 * [minAgeMonths, maxAgeMonths] ni dirisha PANDE ZOTE MBILI ZIKIHUSISHWA, na
 * ndilo linalojibu swali la usalama la FeedService.feedTypesForCycle -
 * angalia hapo kwa sheria ya mwelekeo (kubwa inaweza kula cha wadogo,
 * mdogo hawezi kula cha wakubwa).
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "feed_types")
@Data
@EqualsAndHashCode(callSuper = false, of = "feedTypeId")
public class FeedType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_type_id")
    private Integer feedTypeId;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "min_age_months", nullable = false)
    private Integer minAgeMonths;

    @Column(name = "max_age_months", nullable = false)
    private Integer maxAgeMonths;

    /**
     * Aina inayoachwa kutumika inazimwa, HAIFUTWI: manunuzi na ulishaji wa
     * zamani yanaielekea, na historia ya "tulilisha nini" lazima ibaki
     * inasomeka.
     */
    @Column(nullable = false)
    private boolean active = true;
}
