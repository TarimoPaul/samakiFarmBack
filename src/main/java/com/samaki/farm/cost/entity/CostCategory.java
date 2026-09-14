package com.samaki.farm.cost.entity;

import com.samaki.farm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

/**
 * Aina ya gharama - "Umeme", "Mafuta", "Usafiri", "Mishahara", "Dawa".
 *
 * NI KATALOGI YA KIMFUMO (haina farm_id), kama {@code AssetCategory}
 * (V21), {@code FeedType} (V16) na {@code Species} (V1): jina la aina ya
 * matumizi ni neno la kibiashara linaloshirikiwa na mashamba yote ya
 * kampuni, si mali ya shamba moja. Bila hivyo, "Umeme" ya shamba A na
 * "Umeme" ya shamba B zingekuwa vitu viwili visivyoweza kujumlishwa.
 *
 * NI JEDWALI, SI ENUM, kwa hoja ile ile ya AssetCategory: V1 iliweka
 * {@code costs.category} kama maandishi huru VARCHAR(50), na orodha ya
 * vitu shamba linavyoweza kutumia fedha haina mwisho. Enum ingelazimisha
 * kila kisichokuwemo kwenye "Nyingine" - kundi ambalo jumla yake
 * haielezi chochote, na ndio hasa kundi ambalo mkulima anataka
 * kulichambua.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "cost_categories")
@Data
@EqualsAndHashCode(callSuper = false, of = "costCategoryId")
public class CostCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cost_category_id")
    private Integer costCategoryId;

    @Column(nullable = false, unique = true, length = 80)
    private String name;
}
