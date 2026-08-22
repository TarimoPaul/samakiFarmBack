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
     * Salio la stoo = jumla ya IN kutoa jumla ya OUT. COALESCE inahakikisha
     * shamba lisilo na movement yoyote linarudisha 0 badala ya null.
     *
     * Hii ni @Query (si derived) kwa sababu ni aggregate yenye masharti -
     * haiwezi kuelezwa kwa jina la method. @SQLRestriction ya entity
     * inatumika hapa pia (ni JPQL).
     */
    @Query("""
           SELECT COALESCE(SUM(CASE WHEN m.direction = com.samaki.farm.feed.entity.FeedStockMovement.Direction.IN
                                    THEN m.quantityKg ELSE -m.quantityKg END), 0)
           FROM FeedStockMovement m
           WHERE m.farm.farmId = :farmId
           """)
    BigDecimal sumBalanceByFarmId(@Param("farmId") Integer farmId);
}
