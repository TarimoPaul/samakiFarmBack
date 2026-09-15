package com.samaki.farm.cost.repository;

import com.samaki.farm.cost.entity.Cost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CostRepository extends JpaRepository<Cost, Integer> {

    /** Jumla ya gharama za mzunguko mmoja (angalia sumByCycle). */
    interface CycleAmount {
        Integer getCycleId();
        BigDecimal getAmount();
    }

    /** Jumla ya aina moja ya gharama (angalia ProfitabilityService). */
    interface CategoryAmount {
        Integer getCostCategoryId();
        String getName();
        BigDecimal getAmount();
    }

    /**
     * Gharama za NGAZI YA MZUNGUKO, zikijumlishwa kwa kila mzunguko.
     *
     * HAKUNA kichujio cha cost_date kwa makusudi: gharama ya mzunguko
     * inafuata TAREHE YA MAVUNO ya mzunguko wake, si tarehe yake (bili ya
     * Machi ya mzunguko uliovunwa Aprili ni ya kipindi cha Aprili).
     *
     * NATIVE, si JPQL: JOIN ya JPQL kwenye entity yenye @SQLRestriction
     * ingeweza kuondoa gharama ambazo aina yake imefutwa - fedha
     * iliyotoka haitoweki kwa sababu lebo yake imefutwa. Hapa is_deleted
     * inachujwa kwenye `costs` pekee, kwa uwazi. Orodha TUPU haiulizwi
     * (`IN ()` si SQL halali) - angalia ProfitabilityService.
     */
    @Query(value = """
            SELECT c.cycle_id AS "cycleId", SUM(c.amount) AS "amount"
            FROM costs c
            WHERE c.is_deleted = false
              AND c.cycle_id IN (:cycleIds)
            GROUP BY c.cycle_id
            """, nativeQuery = true)
    List<CycleAmount> sumByCycle(@Param("cycleIds") Collection<Integer> cycleIds);

    /** Gharama za mizunguko hii kwa AINA - ili "Chakula" ionekane kando ya chakula. */
    @Query(value = """
            SELECT cc.cost_category_id AS "costCategoryId", cc.name AS "name",
                   SUM(c.amount) AS "amount"
            FROM costs c
            JOIN cost_categories cc ON cc.cost_category_id = c.cost_category_id
            WHERE c.is_deleted = false
              AND c.cycle_id IN (:cycleIds)
            GROUP BY cc.cost_category_id, cc.name
            ORDER BY cc.name, cc.cost_category_id
            """, nativeQuery = true)
    List<CategoryAmount> sumByCategoryForCycles(@Param("cycleIds") Collection<Integer> cycleIds);

    /**
     * Gharama za SHAMBA ZIMA (cycle_id IS NULL) kwa AINA, ndani ya kipindi
     * kwa cost_date - mipaka yote miwili imo.
     */
    @Query(value = """
            SELECT cc.cost_category_id AS "costCategoryId", cc.name AS "name",
                   SUM(c.amount) AS "amount"
            FROM costs c
            JOIN cost_categories cc ON cc.cost_category_id = c.cost_category_id
            WHERE c.is_deleted = false
              AND c.farm_id = :farmId
              AND c.cycle_id IS NULL
              AND c.cost_date BETWEEN :fromDate AND :toDate
            GROUP BY cc.cost_category_id, cc.name
            ORDER BY cc.name, cc.cost_category_id
            """, nativeQuery = true)
    List<CategoryAmount> sumFarmLevelByCategory(@Param("farmId") Integer farmId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);

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
