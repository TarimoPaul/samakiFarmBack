package com.samaki.farm.productionunit.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.productionunit.dto.CreateProductionUnitInput;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductionUnitService {

    private final ProductionUnitRepository unitRepository;
    private final FarmRepository farmRepository;
    private final PermissionChecker permissionChecker;

    public ProductionUnitService(ProductionUnitRepository unitRepository, FarmRepository farmRepository,
                                  PermissionChecker permissionChecker) {
        this.unitRepository = unitRepository;
        this.farmRepository = farmRepository;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public List<ProductionUnit> listForCurrentFarm() {
        return unitRepository.findByFarm_FarmId(permissionChecker.requireFarmScope("view_dashboard"));
    }

    @Transactional
    public ProductionUnit create(CreateProductionUnitInput input) {
        Integer farmId = permissionChecker.requireFarmScope("manage_units");
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new IllegalArgumentException("Farm haipo"));

        ProductionUnit unit = new ProductionUnit();
        unit.setFarm(farm);
        unit.setCode(input.code());
        unit.setType(parseType(input.type()));
        if (input.sizeM3() != null) {
            unit.setSizeM3(BigDecimal.valueOf(input.sizeM3()));
        }
        unit.setWaterSource(input.waterSource());
        unit.setStatus("IDLE");

        return unitRepository.save(unit);
    }

    /**
     * UnitType.valueOf(...) ikikataa, ujumbe wake ni "No enum constant
     * com.samaki.farm.productionunit.entity.ProductionUnit.UnitType.LAKE" -
     * jina la darasa la ndani likielekea kwa mteja, na lisilomweleza ni
     * aina zipi zinazokubalika (angalia FRONTEND_BACKEND_AUDIT.md, D-8).
     *
     * Orodha inatoka kwa enum yenyewe, si kwa maandishi ya mkono, hivyo
     * aina mpya ikiongezwa ujumbe unajisasisha. (Enum na CHECK ya V1
     * zinapaswa kubaki zikilingana - angalia ProductionUnit.UnitType.)
     */
    private static ProductionUnit.UnitType parseType(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return ProductionUnit.UnitType.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException unknownType) {
                // inaangukia kwenye ujumbe wa pamoja hapa chini
            }
        }
        String allowed = Arrays.stream(ProductionUnit.UnitType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Aina ya kitengo si sahihi. Chagua: " + allowed + ".");
    }
}
