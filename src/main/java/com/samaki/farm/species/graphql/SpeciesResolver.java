package com.samaki.farm.species.graphql;

import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.services.SpeciesService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/** GraphQL mapping pekee - logic iko SpeciesService. */
@Controller
public class SpeciesResolver {

    private final SpeciesService speciesService;

    public SpeciesResolver(SpeciesService speciesService) {
        this.speciesService = speciesService;
    }

    @QueryMapping
    public List<Species> species() {
        return speciesService.listAll();
    }

    // Hoja tatu tambulifu badala ya input type, kwa mfuatano ule ule wa
    // createFeedType: katalogi ina safu tatu tu zinazoandikwa.
    @MutationMapping
    public Species createSpecies(@Argument String name,
                                  @Argument Double growthMonthsAvg,
                                  @Argument Double avgHarvestWeightKg) {
        return speciesService.create(name, growthMonthsAvg, avgHarvestWeightKg);
    }
}
