package com.samaki.farm.finance.graphql;

import com.samaki.farm.finance.dto.CycleProfitability;
import com.samaki.farm.finance.dto.FarmProfitability;
import com.samaki.farm.finance.services.ProfitabilityService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** GraphQL mapping pekee - hesabu yote iko ProfitabilityService. */
@Controller
public class ProfitabilityResolver {

    private final ProfitabilityService profitabilityService;

    public ProfitabilityResolver(ProfitabilityService profitabilityService) {
        this.profitabilityService = profitabilityService;
    }

    @QueryMapping
    public CycleProfitability cycleProfitability(@Argument Integer cycleId) {
        return profitabilityService.cycleProfitability(cycleId);
    }

    @QueryMapping
    public FarmProfitability farmProfitability(@Argument Integer farmId,
                                               @Argument String fromDate,
                                               @Argument String toDate) {
        return profitabilityService.farmProfitability(farmId, fromDate, toDate);
    }
}
