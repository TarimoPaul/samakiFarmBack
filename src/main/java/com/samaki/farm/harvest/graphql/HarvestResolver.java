package com.samaki.farm.harvest.graphql;

import com.samaki.farm.cycle.services.FinanceVisibility;
import com.samaki.farm.harvest.entity.HarvestEvent;
import com.samaki.farm.harvest.services.HarvestService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/** GraphQL mapping pekee - logic iko HarvestService. */
@Controller
public class HarvestResolver {

    private final HarvestService harvestService;
    private final FinanceVisibility financeVisibility;

    public HarvestResolver(HarvestService harvestService, FinanceVisibility financeVisibility) {
        this.harvestService = harvestService;
        this.financeVisibility = financeVisibility;
    }

    @QueryMapping
    public List<HarvestEvent> harvestEvents(@Argument Integer cycleId) {
        return harvestService.listForCycle(cycleId);
    }

    // Hoja tambulifu, kwa mfuatano ule ule wa closeCycle/createCost.
    @MutationMapping
    public HarvestEvent recordHarvestEvent(@Argument Integer cycleId,
                                           @Argument String eventDate,
                                           @Argument Integer fishCount,
                                           @Argument Double weightKg,
                                           @Argument String reason,
                                           @Argument Double saleAmount) {
        return harvestService.record(cycleId, eventDate, fishCount, weightKg, reason, saleAmount);
    }

    @MutationMapping
    public boolean deleteHarvestEvent(@Argument Integer harvestEventId) {
        return harvestService.delete(harvestEventId);
    }

    @SchemaMapping(typeName = "HarvestEvent", field = "cycleId")
    public Integer cycleId(HarvestEvent event) {
        return event.getCycle().getCycleId();
    }

    @SchemaMapping(typeName = "HarvestEvent", field = "reason")
    public String reason(HarvestEvent event) {
        return event.getReason().name();
    }

    /**
     * FEDHA: null bila `view_finance`. Uga, si query - hivyo jibu la
     * recordHarvestEvent linafuata sheria ile ile ya orodha.
     */
    @SchemaMapping(typeName = "HarvestEvent", field = "saleAmount")
    public BigDecimal saleAmount(HarvestEvent event) {
        return financeVisibility.visible(event.getSaleAmount());
    }
}
