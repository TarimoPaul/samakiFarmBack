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
}
