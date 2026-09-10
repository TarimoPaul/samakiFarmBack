package com.samaki.farm.asset.repository;

import com.samaki.farm.asset.entity.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Integer> {

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
