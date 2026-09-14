package com.samaki.farm.cost.graphql;

import com.samaki.farm.cost.dto.CycleRef;
import com.samaki.farm.cost.entity.Cost;
import com.samaki.farm.cost.entity.CostCategory;
import com.samaki.farm.cost.services.CostService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL mapping pekee - logic iko CostService.
 *
 * HAKUNA endpoint ya jumla (jumla ya mzunguko, ya shamba, ya kampuni)
 * kwa makusudi, kwa hoja ile ile ya AssetResolver: `costs` inarudisha
 * mistari yenye shamba na mzunguko wake, na kuzijumlisha ni kazi ya
 * frontend. Backend ingelazimika kuchagua mgawanyo - kwa mzunguko? kwa
 * shamba? kwa aina? kwa mwezi? - na kila skrini mpya ingedai endpoint
 * yake. Hapa mgawanyo ni MWINGI zaidi kuliko kwenye mali (mzunguko
 * unaongezeka), hivyo hoja ina nguvu zaidi, si kidogo.
 */
@Controller
public class CostResolver {

    private final CostService costService;

    public CostResolver(CostService costService) {
        this.costService = costService;
    }

    @QueryMapping
    public List<Cost> costs() {
        return costService.listCosts();
    }

    @QueryMapping
    public List<CostCategory> costCategories() {
        return costService.listCategories();
    }

    /**
     * Kichagua-mzunguko cha fomu ya gharama: mizunguko YOTE ya shamba
     * moja, inayoendelea na iliyofungwa (angalia CostService).
     */
    @QueryMapping
    public List<CycleRef> farmCycles(@Argument Integer farmId) {
        return costService.listFarmCycles(farmId);
    }

    /**
     * `Cost.cycle` ni LEBO, si Cycle nzima - na inaweza kuwa null.
     *
     * Field resolver (si uga wa entity) kwa sababu lebo inajengwa kutoka
     * majedwali matatu; ni mtindo ule ule wa CycleResolver.speciesName.
     * Null inapita kama null: gharama ya shamba zima haina mzunguko, na
     * schema inasema `cycle: CycleRef` (si `!`) kwa sababu hiyo.
     */
    @SchemaMapping(typeName = "Cost", field = "cycle")
    public CycleRef cycle(Cost cost) {
        return CycleRef.of(cost.getCycle());
    }

    // Hoja tambulifu badala ya input type, kwa mfuatano ule ule wa
    // createAsset/createSpecies/createFeedType.
    @MutationMapping
    public Cost createCost(@Argument Integer farmId,
                           @Argument Integer cycleId,
                           @Argument Integer costCategoryId,
                           @Argument Double amount,
                           @Argument String costDate,
                           @Argument String description) {
        return costService.createCost(farmId, cycleId, costCategoryId, amount, costDate, description);
    }

    @MutationMapping
    public CostCategory createCostCategory(@Argument String name) {
        return costService.createCategory(name);
    }
}
