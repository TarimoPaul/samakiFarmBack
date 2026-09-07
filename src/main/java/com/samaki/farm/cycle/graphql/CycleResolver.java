package com.samaki.farm.cycle.graphql;

import com.samaki.farm.cycle.dto.CreateCycleInput;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.services.CycleService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL mapping pekee - logic yote (FR-3.2 expected_harvest_date, FR-4.1
 * daily_tasks) iko CycleService.
 */
@Controller
public class CycleResolver {

    private final CycleService cycleService;

    public CycleResolver(CycleService cycleService) {
        this.cycleService = cycleService;
    }

    @QueryMapping
    public List<Cycle> cycles(@Argument String status) {
        return cycleService.listForCurrentFarm(status);
    }

    // Field resolver - GraphQL schema inaomba "speciesName" (String), si species
    // nzima; hii inatatua field hiyo kutoka kwenye uhusiano wa Cycle -> Species.
    // Inabaki hapa (si service) kwa sababu ni mapping ya schema, si logic.
    @SchemaMapping(typeName = "Cycle", field = "speciesName")
    public String speciesName(Cycle cycle) {
        return cycle.getSpecies().getName();
    }

    @MutationMapping
    public Cycle createCycle(@Argument CreateCycleInput input) {
        return cycleService.create(input);
    }

    /**
     * Hoja tambulifu (si input type) kwa mfuatano ule ule wa schema -
     * mtindo ule ule wa createFeedType/updateFeedType.
     *
     * HAKUNA `survivalRate` hapa, na hiyo ni sehemu ya mkataba:
     * actualSurvivalRate inakokotolewa na database kutoka harvestedCount
     * na fingerlingsCount (angalia V19), hivyo hakuna njia ya mteja
     * kuipandikiza - kama ilivyo kwa FeedPurchase.totalCost.
     */
    @MutationMapping
    public Cycle closeCycle(@Argument Integer cycleId,
                             @Argument String outcome,
                             @Argument String actualHarvestDate,
                             @Argument Integer harvestedCount,
                             @Argument Double totalWeightKg,
                             @Argument String notes) {
        return cycleService.closeCycle(cycleId, outcome, actualHarvestDate,
                harvestedCount, totalWeightKg, notes);
    }
}
