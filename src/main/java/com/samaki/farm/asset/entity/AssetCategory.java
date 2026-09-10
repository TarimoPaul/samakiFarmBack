package com.samaki.farm.asset.entity;

import com.samaki.farm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

/**
 * Aina ya mali - "Jengo", "Gari", "Pampu", "Nyavu".
 *
 * NI KATALOGI YA KIMFUMO (haina farm_id), kama {@code Species} (V1) na
 * {@code FeedType} (V16): jina la aina ya kitu ni neno la kibiashara
 * linaloshirikiwa na mashamba yote ya kampuni, si mali ya shamba moja.
 *
 * NI JEDWALI, SI ENUM, na hiyo ndiyo hoja nzima. V1 iliweka
 * {@code assets.category} kama maandishi huru yenye maoni "Kifaa, Jengo,
 * Gari, n.k." - orodha isiyo na mwisho iliyoandikwa kwenye maoni. Enum
 * ingelazimisha orodha hiyo kwenye msimbo, na kila kitu kisichokuwemo
 * kingelazimika kuingia kwenye "Nyingine" - yaani kundi lisilo na maana
 * ambalo jumla yake haielezi chochote. Mkulima anaunda anachohitaji.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "asset_categories")
@Data
@EqualsAndHashCode(callSuper = false, of = "assetCategoryId")
public class AssetCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_category_id")
    private Integer assetCategoryId;

    @Column(nullable = false, unique = true, length = 80)
    private String name;
}
