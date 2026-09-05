package com.samaki.farm.feed.graphql;

import com.samaki.farm.feed.dto.FeedStockBalance;
import com.samaki.farm.feed.dto.FeedTypesForCycle;
import com.samaki.farm.feed.dto.LogFeedingInput;
import com.samaki.farm.feed.dto.RecordFeedPurchaseInput;
import com.samaki.farm.feed.dto.SuitableFeedType;
import com.samaki.farm.feed.entity.FeedPurchase;
import com.samaki.farm.feed.entity.FeedStockMovement;
import com.samaki.farm.feed.entity.FeedType;
import com.samaki.farm.feed.entity.FeedingLog;
import com.samaki.farm.feed.services.FeedService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

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

    // Ilikuwa Float! moja ya shamba zima; sasa ni mstari kwa kila aina ya
    // chakula (angalia FeedStockBalance kwa sababu).
    @QueryMapping
    public List<FeedStockBalance> feedStockBalance() {
        return feedService.feedStockBalance();
    }

    @QueryMapping
    public List<FeedType> feedTypes(@Argument Boolean activeOnly) {
        return feedService.listFeedTypes(activeOnly);
    }

    @QueryMapping
    public FeedTypesForCycle feedTypesForCycle(@Argument Integer cycleId) {
        return feedService.feedTypesForCycle(cycleId);
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

    // Enum -> String kwa uwazi, mtindo ule ule wa `direction` hapo juu:
    // schema ya module hii inatumia String kwa hali zote (status, direction),
    // hivyo mteja mmoja anashughulikia zote kwa njia ile ile.
    @SchemaMapping(typeName = "SuitableFeedType", field = "suitability")
    public String suitability(SuitableFeedType entry) {
        return entry.suitability().name();
    }

    @MutationMapping
    public FeedPurchase recordFeedPurchase(@Argument RecordFeedPurchaseInput input) {
        return feedService.recordPurchase(input);
    }

    @MutationMapping
    public FeedingLog logFeeding(@Argument LogFeedingInput input) {
        return feedService.logFeeding(input);
    }

    // Hoja tatu tambulifu badala ya input type: katalogi ina safu tatu tu
    // zinazoandikwa, na `active` haichaguliwi wakati wa kuunda.
    @MutationMapping
    public FeedType createFeedType(@Argument String name,
                                    @Argument Integer minAgeMonths,
                                    @Argument Integer maxAgeMonths) {
        return feedService.createFeedType(name, minAgeMonths, maxAgeMonths);
    }
}
