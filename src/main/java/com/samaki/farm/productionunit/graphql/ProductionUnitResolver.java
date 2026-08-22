package com.samaki.farm.productionunit.graphql;

import com.samaki.farm.productionunit.dto.CreateProductionUnitInput;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.services.ProductionUnitService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/** GraphQL mapping pekee - logic iko ProductionUnitService. */
@Controller
public class ProductionUnitResolver {

    private final ProductionUnitService productionUnitService;

    public ProductionUnitResolver(ProductionUnitService productionUnitService) {
        this.productionUnitService = productionUnitService;
    }

    @QueryMapping
    public List<ProductionUnit> productionUnits() {
        return productionUnitService.listForCurrentFarm();
    }

    @MutationMapping
    public ProductionUnit createProductionUnit(@Argument CreateProductionUnitInput input) {
        return productionUnitService.create(input);
    }
}
