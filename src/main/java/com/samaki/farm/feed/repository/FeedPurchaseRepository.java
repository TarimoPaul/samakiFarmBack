package com.samaki.farm.feed.repository;

import com.samaki.farm.feed.entity.FeedPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedPurchaseRepository extends JpaRepository<FeedPurchase, Integer> {

    List<FeedPurchase> findByFarm_FarmIdOrderByPurchaseDateDesc(Integer farmId);
}
