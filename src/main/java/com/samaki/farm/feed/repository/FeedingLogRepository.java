package com.samaki.farm.feed.repository;

import com.samaki.farm.feed.entity.FeedingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedingLogRepository extends JpaRepository<FeedingLog, Integer> {

    List<FeedingLog> findByCycle_CycleIdOrderByLogDateDesc(Integer cycleId);

    /** Scoping ya shamba inapitia cycle -> unit -> farm (feeding_logs haina farm_id). */
    List<FeedingLog> findByCycle_Unit_Farm_FarmIdOrderByLogDateDesc(Integer farmId);

    /**
     * Ulishaji mangapi unaelekea aina hii? Ni swali la
     * FeedService.deleteFeedType, si takwimu.
     *
     * Ni derived query, hivyo @SQLRestriction inaichuja: ulishaji
     * uliofutwa haumzuii mtu kufuta aina, kwa sababu wenyewe hauonekani
     * popote.
     */
    long countByFeedType_FeedTypeId(Integer feedTypeId);
}
