package com.samaki.farm.cycle.graphql;

import com.samaki.farm.cycle.dto.CreateCycleInput;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.services.CycleService;
import com.samaki.farm.cycle.services.FinanceVisibility;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphQL mapping pekee - logic yote (FR-3.2 expected_harvest_date, FR-4.1
 * daily_tasks) iko CycleService.
 */
@Controller
public class CycleResolver {

    private final CycleService cycleService;
    private final FinanceVisibility financeVisibility;

    public CycleResolver(CycleService cycleService, FinanceVisibility financeVisibility) {
        this.cycleService = cycleService;
        this.financeVisibility = financeVisibility;
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
     * HAKUNA `survivalRate` hapa, WALA idadi, uzito au mapato (V25):
     * zote zinajumlishwa kutoka matukio ya mavuno na CycleService, na
     * actualSurvivalRate inakokotolewa na database kutoka kwa jumla hiyo.
     * Hakuna namba ya mteja inayoingia kwenye matokeo ya mzunguko.
     */
    @MutationMapping
    public Cycle closeCycle(@Argument Integer cycleId,
                             @Argument String outcome,
                             @Argument String actualHarvestDate,
                             @Argument String notes) {
        return cycleService.closeCycle(cycleId, outcome, actualHarvestDate, notes);
    }

    // ---- FEDHA: null bila `view_finance` - angalia FinanceVisibility ----
    //
    // Resolver za UGA, si za query: `Cycle` inafikiwa pia kupitia
    // createCycle, closeCycle na FeedingLog.cycle (ya WORKER). Uga
    // ukiombwa kwa njia yoyote, unapita hapa.

    @SchemaMapping(typeName = "Cycle", field = "fingerlingCost")
    public BigDecimal fingerlingCost(Cycle cycle) {
        return financeVisibility.visible(cycle.getFingerlingCost());
    }

    @SchemaMapping(typeName = "Cycle", field = "totalRevenue")
    public BigDecimal totalRevenue(Cycle cycle) {
        return financeVisibility.visible(cycle.getTotalRevenue());
    }
}
