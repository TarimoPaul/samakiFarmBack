package com.samaki.farm.cost.repository;

import com.samaki.farm.cost.entity.Cost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CostRepository extends JpaRepository<Cost, Integer> {

    /**
     * Daftari la MASHAMBA MENGI kwa ombi moja - swali pekee la module hii.
     *
     * `In` (si `equals`) ni tofauti ile ile ya AssetRepository: mmiliki
     * mwenye mashamba matatu anapata gharama zote kwenye orodha moja, si
     * tatu. Orodha ya vitambulisho inatoka
     * FarmMembershipService.callersFarmIds - uanachama halisi, si kitu
     * kinachotoka kwa mteja.
     *
     * ORODHA TUPU HAIULIZWI DATABASE - angalia CostService.listCosts.
     * `farm_id IN ()` si SQL halali.
     *
     * @EntityGraph inavuta `cycle` PIA, na hiyo ni muhimu zaidi hapa
     * kuliko kwenye mali: lebo ya mzunguko inajengwa kutoka
     * cycle.unit.code na cycle.species.name, hivyo bila fetch hii kila
     * mstari wenye mzunguko ungegharimu maswali matatu ya ziada. Njia ya
     * `cycle.unit.farm` haihitajiki kwa lebo, lakini `unit` na `species`
     * zinahitajika - zinaorodheshwa kwa uwazi.
     *
     * MPYA KWANZA: gharama ya juzi ndiyo inayotafutwa, na index ya V23
     * (farm_id, cost_date DESC) inafuata mpangilio huu hasa. costId
     * inavunja sare ili mpangilio uwe thabiti kwa gharama za siku moja.
     */
    @EntityGraph(attributePaths = {"farm", "costCategory", "cycle", "cycle.unit", "cycle.species"})
    List<Cost> findByFarm_FarmIdInOrderByCostDateDescCostIdDesc(Collection<Integer> farmIds);

    /** Gharama ngapi zinaelekea aina hii - ulinzi wa kufuta aina, siku itakapoongezwa. */
    long countByCostCategory_CostCategoryId(Integer costCategoryId);
}
