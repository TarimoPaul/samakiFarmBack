package com.samaki.farm.feed.repository;

import com.samaki.farm.feed.entity.FeedPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FeedPurchaseRepository extends JpaRepository<FeedPurchase, Integer> {

    List<FeedPurchase> findByFarm_FarmIdOrderByPurchaseDateDesc(Integer farmId);

    /**
     * Manunuzi ya shamba ndani ya kipindi (mipaka yote miwili imo) -
     * PAMOJA na yaliyobatilishwa. Kuyaondoa ni kazi ya mwombaji kwa
     * FeedStockMovementRepository.findReversedPurchaseIds: leja ndiyo
     * inayojua kubatilisha (angalia ProfitabilityService.feedCost).
     */
    List<FeedPurchase> findByFarm_FarmIdAndPurchaseDateBetween(Integer farmId, LocalDate fromDate,
                                                               LocalDate toDate);

    /** Manunuzi mangapi yanaelekea aina hii - angalia FeedService.deleteFeedType. */
    long countByFeedType_FeedTypeId(Integer feedTypeId);
}
