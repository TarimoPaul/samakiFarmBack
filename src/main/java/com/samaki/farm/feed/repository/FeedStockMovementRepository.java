package com.samaki.farm.feed.repository;

import com.samaki.farm.feed.entity.FeedStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface FeedStockMovementRepository extends JpaRepository<FeedStockMovement, Integer> {

    List<FeedStockMovement> findByFarm_FarmIdOrderByMovedAtDesc(Integer farmId);

    /**
     * Je, shamba hili lina movement yoyote ya aina hii? Ndilo swali la
     * idempotency la DevFeedSeedService: stoo ya kuanzia inawekwa mara moja
     * tu, na ikiwa shamba tayari lina historia ya aina hiyo - iwe ya seed au
     * ya kweli - haiguswi.
     */
    boolean existsByFarm_FarmIdAndFeedType_FeedTypeId(Integer farmId, Integer feedTypeId);

    /**
     * Salio la stoo = jumla ya IN kutoa jumla ya OUT, KWA KILA AINA ya
     * chakula ndani ya shamba.
     *
     * Awali ilirudisha namba MOJA ya shamba zima. Ilikuwa ikichanganya vitu
     * visivyochanganyika: kilo za chakula cha vifaranga na za wakubwa
     * zilijumlishwa pamoja, hivyo +50 ya moja na -50 ya nyingine
     * zilighairiana hadi 0 na ghala lenye chakula likaonekana tupu.
     *
     * COALESCE ya zamani ILIONDOKA pamoja na maana yake: shamba lisilo na
     * movement yoyote sasa linarudisha ORODHA TUPU, si mstari wa sifuri -
     * hakuna aina ya kuweka kwenye mstari huo. (Ndicho maana yake sahihi:
     * "hakuna chakula chochote", si "kuna aina moja yenye kilo sifuri".)
     * COALESCE inabaki kwenye SUM kwa usalama wa aina ya data pekee.
     *
     * KWA NINI feedTypeId badala ya FeedType nzima. GROUP BY ya entity
     * ingelazimu safu ZOTE za feed_types ziwe kwenye GROUP BY ili Postgres
     * ikubali - ni SQL dhaifu inayovunjika kila safu mpya inapoongezwa.
     * Kitambulisho pekee kinatosha; FeedService inazipakia entity mara moja
     * kwa findAllById.
     */
    @Query("""
           SELECT m.feedType.feedTypeId AS feedTypeId,
                  COALESCE(SUM(CASE WHEN m.direction = com.samaki.farm.feed.entity.FeedStockMovement.Direction.IN
                                    THEN m.quantityKg ELSE -m.quantityKg END), 0) AS quantityKg
           FROM FeedStockMovement m
           WHERE m.farm.farmId = :farmId
           GROUP BY m.feedType.feedTypeId
           """)
    List<FeedTypeBalanceRow> sumBalanceByFarmId(@Param("farmId") Integer farmId);

    /** Mstari mmoja wa salio: aina (kwa kitambulisho) na kilo zilizobaki. */
    interface FeedTypeBalanceRow {
        Integer getFeedTypeId();
        BigDecimal getQuantityKg();
    }
}
