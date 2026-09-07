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
     * Movement ngapi za leja zinaelekea aina hii, kwenye shamba LOLOTE -
     * angalia FeedService.deleteFeedType.
     *
     * Bila shamba kwa makusudi: katalogi ni ya kimfumo, hivyo swali la
     * "aina hii inatumika?" ni la mfumo mzima. Kuuliza kwa shamba moja
     * kungeruhusu msimamizi wa shamba A kufuta aina ambayo shamba B lina
     * kilo zake ghalani.
     */
    long countByFeedType_FeedTypeId(Integer feedTypeId);

    /**
     * Vitambulisho vya manunuzi ambayo TAYARI YAMEBATILISHWA.
     *
     * Ubatilishaji ni movement ya OUT inayoelekea ununuzi (angalia
     * FeedService.reverseFeedPurchase) - hivyo leja YENYEWE ndiyo inayojibu
     * "je, ununuzi huu umebatilishwa?". Hakuna safu ya `reversed_at` kwenye
     * feed_purchases, na hiyo ni kwa makusudi: safu kama hiyo ingekuwa nakala
     * ya ukweli ulio kwenye leja, na nakala inaweza kuachana na asili yake.
     * Leja ndiyo rekodi; kila kitu kingine kinasomwa kutoka kwake.
     *
     * SWALI MOJA KWA ORODHA NZIMA, si moja kwa kila mstari: listPurchases
     * inaita hii mara moja na kutumia Set inayorudi.
     */
    @Query("""
           SELECT m.referencePurchaseId FROM FeedStockMovement m
           WHERE m.farm.farmId = :farmId
             AND m.direction = com.samaki.farm.feed.entity.FeedStockMovement.Direction.OUT
             AND m.referencePurchaseId IS NOT NULL
           """)
    List<Integer> findReversedPurchaseIds(@Param("farmId") Integer farmId);

    /** Je, ununuzi huu umeshabatilishwa? Kikwazo cha kubatilisha mara mbili. */
    boolean existsByReferencePurchaseIdAndDirection(
            Integer referencePurchaseId, FeedStockMovement.Direction direction);

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

    /**
     * Salio la AINA MOJA ndani ya shamba - hesabu ILE ILE ya
     * sumBalanceByFarmId hapo juu, ikiulizwa kwa aina moja.
     *
     * Ni query yake badala ya kuchuja orodha ya sumBalanceByFarmId kwa
     * sababu mtumizi wake (FeedService.feedTypeDeactivationImpact) anataka
     * aina moja tu; kuvuta salio la kila aina ya shamba ili kutupa zote ila
     * moja ni kazi bure. Ikitofautiana na dada yake siku moja, skrini ya
     * ulishaji na onyo la kuzima zingeonyesha kilo TOFAUTI za kitu kimoja -
     * ndiyo maana CASE ni ile ile herufi kwa herufi.
     *
     * COALESCE INAMAANISHA KITU HAPA, tofauti na kwenye orodha: aina isiyo
     * na movement yoyote kwenye shamba hili ina kilo SIFURI - jibu halali
     * la "kuna nini ghalani", si mstari usiokuwepo. (Kwenye orodha, mstari
     * wa sifuri ungekuwa kelele; hapa null ingekuwa "sijui".)
     */
    @Query("""
           SELECT COALESCE(SUM(CASE WHEN m.direction = com.samaki.farm.feed.entity.FeedStockMovement.Direction.IN
                                    THEN m.quantityKg ELSE -m.quantityKg END), 0)
           FROM FeedStockMovement m
           WHERE m.farm.farmId = :farmId
             AND m.feedType.feedTypeId = :feedTypeId
           """)
    BigDecimal sumBalanceByFarmIdAndFeedTypeId(@Param("farmId") Integer farmId,
                                                @Param("feedTypeId") Integer feedTypeId);

    /** Mstari mmoja wa salio: aina (kwa kitambulisho) na kilo zilizobaki. */
    interface FeedTypeBalanceRow {
        Integer getFeedTypeId();
        BigDecimal getQuantityKg();
    }
}
