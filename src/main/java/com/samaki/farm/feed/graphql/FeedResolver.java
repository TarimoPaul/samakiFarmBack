package com.samaki.farm.feed.graphql;

import com.samaki.farm.feed.dto.FeedPurchaseView;
import com.samaki.farm.feed.dto.FeedStockBalance;
import com.samaki.farm.feed.dto.FeedTypeDeactivationImpact;
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

    // FeedPurchaseView, si FeedPurchase: mstari unaorudishwa hapa unaweza
    // kuwa umefichwa bei (unitCost/totalCost = null bila `view_feed_cost` -
    // angalia FeedService.listPurchases). Type ya schema ni ile ile
    // `FeedPurchase`; kinachotofautiana ni class ya Java, ambayo GraphQL
    // haiitaji kuijua - property zinasomwa kwa jina.
    @QueryMapping
    public List<FeedPurchaseView> feedPurchases() {
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

    // Query, si sehemu ya jibu la setFeedTypeActive: onyo linahitajika KABLA
    // ya kubofya, na mutation inarudisha hali ya BAADA.
    @QueryMapping
    public FeedTypeDeactivationImpact feedTypeDeactivationImpact(@Argument Integer feedTypeId) {
        return feedService.feedTypeDeactivationImpact(feedTypeId);
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
    public FeedPurchaseView recordFeedPurchase(@Argument RecordFeedPurchaseInput input) {
        // View, si entity: schema ina `reversed`, ambayo si safu ya jedwali -
        // inasomwa kwenye leja. `full(...)` kwa sababu jibu la mutation
        // HALIFICHWI: mnunuzi ndiye aliyeandika bei hii sekunde iliyopita.
        // Ununuzi mpya haujabatilishwa, kwa hiyo `false`.
        return FeedPurchaseView.full(feedService.recordPurchase(input), false);
    }

    @MutationMapping
    public FeedingLog logFeeding(@Argument LogFeedingInput input) {
        return feedService.logFeeding(input);
    }

    // Kubatilisha, si kufuta: inaandika movement ya OUT ya kilo zile zile.
    // Inarudisha ununuzi ULIOBATILISHWA - unabaki kwenye orodha.
    @MutationMapping
    public FeedPurchaseView reverseFeedPurchase(@Argument Integer purchaseId) {
        // `true` bila kuuliza leja tena: mutation imekwisha kuiandika
        // movement ya OUT, ndani ya transaction hii hii.
        return FeedPurchaseView.full(feedService.reverseFeedPurchase(purchaseId), true);
    }

    // Kubatilisha + kurekodi upya, kwenye transaction moja. Inarudisha
    // ununuzi MPYA.
    @MutationMapping
    public FeedPurchaseView correctFeedPurchase(@Argument Integer purchaseId,
                                                 @Argument RecordFeedPurchaseInput input) {
        // Inarudisha ununuzi MPYA - hivyo `false`. Wa zamani ndiye
        // aliyebatilishwa, na anaonekana hivyo kwenye orodha.
        return FeedPurchaseView.full(feedService.correctFeedPurchase(purchaseId, input), false);
    }

    // Hoja tatu tambulifu badala ya input type: katalogi ina safu tatu tu
    // zinazoandikwa, na `active` haichaguliwi wakati wa kuunda.
    @MutationMapping
    public FeedType createFeedType(@Argument String name,
                                    @Argument Integer minAgeMonths,
                                    @Argument Integer maxAgeMonths) {
        return feedService.createFeedType(name, minAgeMonths, maxAgeMonths);
    }

    // Hoja tambulifu tena, kwa mfuatano ule ule wa createFeedType: kitu
    // kinachohaririwa ni safu zile zile tatu, hivyo kubadilisha kuwa input
    // type hapa pekee kungefanya mutation mbili zinazoandika kitu kimoja
    // zionekane tofauti bila sababu.
    @MutationMapping
    public FeedType updateFeedType(@Argument Integer feedTypeId,
                                    @Argument String name,
                                    @Argument Integer minAgeMonths,
                                    @Argument Integer maxAgeMonths) {
        return feedService.updateFeedType(feedTypeId, name, minAgeMonths, maxAgeMonths);
    }

    @MutationMapping
    public FeedType setFeedTypeActive(@Argument Integer feedTypeId, @Argument Boolean active) {
        return feedService.setFeedTypeActive(feedTypeId, active);
    }

    // Inarudisha Boolean, si FeedType: baada ya kufuta hakuna aina ya
    // kurudisha - kila query ingeificha - na `true` pekee ndiyo taarifa
    // iliyobaki. Kushindwa kunatoka kama hitilafu, si kama `false`.
    @MutationMapping
    public Boolean deleteFeedType(@Argument Integer feedTypeId) {
        feedService.deleteFeedType(feedTypeId);
        return true;
    }
}
