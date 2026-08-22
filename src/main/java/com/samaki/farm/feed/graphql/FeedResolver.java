package com.samaki.farm.feed.graphql;

import com.samaki.farm.feed.dto.LogFeedingInput;
import com.samaki.farm.feed.dto.RecordFeedPurchaseInput;
import com.samaki.farm.feed.entity.FeedPurchase;
import com.samaki.farm.feed.entity.FeedStockMovement;
import com.samaki.farm.feed.entity.FeedingLog;
import com.samaki.farm.feed.services.FeedService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/** GraphQL mapping pekee - logic iko FeedService. */
@Controller
public class FeedResolver {

    private final FeedService feedService;

    public FeedResolver(FeedService feedService) {
        this.feedService = feedService;
    }

    @QueryMapping
    public List<FeedPurchase> feedPurchases() {
        return feedService.listPurchases();
    }

    @QueryMapping
    public List<FeedingLog> feedingLogs(@Argument Integer cycleId) {
        return feedService.listFeedingLogs(cycleId);
    }

    @QueryMapping
    public List<FeedStockMovement> feedStockMovements() {
        return feedService.listStockMovements();
    }

    @QueryMapping
    public BigDecimal feedStockBalance() {
        return feedService.feedStockBalance();
    }

    // Schema inaomba jina la mtu (String), si FarmUser nzima - mtindo ule ule
    // wa Cycle.speciesName.
    @SchemaMapping(typeName = "FeedingLog", field = "recordedByName")
    public String recordedByName(FeedingLog log) {
        return log.getRecordedBy() == null ? null : log.getRecordedBy().getName();
    }

    @SchemaMapping(typeName = "FeedStockMovement", field = "direction")
    public String direction(FeedStockMovement movement) {
        return movement.getDirection().name();
    }

    @MutationMapping
    public FeedPurchase recordFeedPurchase(@Argument RecordFeedPurchaseInput input) {
        return feedService.recordPurchase(input);
    }

    @MutationMapping
    public FeedingLog logFeeding(@Argument LogFeedingInput input) {
        return feedService.logFeeding(input);
    }
}
