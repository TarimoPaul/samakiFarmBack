package com.samaki.farm.asset.repository;

import com.samaki.farm.asset.entity.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Integer> {

    /**
     * MTAJI wa shamba ulionunuliwa ndani ya kipindi (acquired_date, mipaka
     * yote miwili imo). Ni namba ya KANDO ya faida - mali si gharama ya
     * uendeshaji, na ProfitabilityService haiiweki kwenye farmNetProfit.
     */
    @Query(value = """
            SELECT COALESCE(SUM(a.cost), 0)
            FROM assets a
            WHERE a.is_deleted = false
              AND a.farm_id = :farmId
              AND a.acquired_date BETWEEN :fromDate AND :toDate
            """, nativeQuery = true)
    BigDecimal sumCostAcquiredBetween(@Param("farmId") Integer farmId,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate);

    /**
     * Daftari la MASHAMBA MENGI kwa ombi moja - ndiyo swali pekee la
     * module hii.
     *
     * `In` (si `equals`) ndiyo tofauti nzima kati ya query hii na kila
     * query nyingine ya data kwenye repo: mmiliki mwenye mashamba
     * matatu anayapata yote kwenye orodha moja, si tatu. Orodha ya
     * vitambulisho inatoka AssetService.callersFarmIds - uanachama wake
     * halisi, si kitu kinachotoka kwa mteja.
     *
     * ORODHA TUPU HAIULIZWI DATABASE - angalia AssetService.listAssets.
     * `farm_id IN ()` si SQL halali, na Hibernate ingeitengeneza kwa
     * `1=0` kimyakimya; kuiepuka kabisa ni wazi zaidi.
     *
     * @EntityGraph: kila mstari unabeba shamba lake NA aina yake kwenye
     * GraphQL. Bila hii ni N+1 ya maswali mawili kwa kila mali - jambo
     * lisiloonekana kwenye majaribio ya mistari mitatu na linaloonekana
     * sana kwenye daftari la kampuni nzima.
     *
     * MPYA KWANZA: mali iliyonunuliwa juzi ndiyo mkulima anayoitafuta,
     * na index ya V21 (farm_id, acquired_date DESC) inafuata mpangilio
     * huu hasa.
     */
    @EntityGraph(attributePaths = {"farm", "assetCategory"})
    List<Asset> findByFarm_FarmIdInOrderByAcquiredDateDescAssetIdDesc(Collection<Integer> farmIds);

    /** Mali ngapi zinaelekea aina hii - ulinzi wa kufuta aina, siku itakapoongezwa. */
    long countByAssetCategory_AssetCategoryId(Integer assetCategoryId);
}
