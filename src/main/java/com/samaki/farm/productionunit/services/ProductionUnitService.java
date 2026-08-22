package com.samaki.farm.productionunit.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.productionunit.dto.CreateProductionUnitInput;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        return unitRepository.findByFarm_FarmId(user.getFarmId());
    }

    @Transactional
    public ProductionUnit create(CreateProductionUnitInput input) {
        AuthenticatedUser user = permissionChecker.require("manage_units");
        Farm farm = farmRepository.findById(user.getFarmId())
                .orElseThrow(() -> new IllegalArgumentException("Farm haipo"));

        ProductionUnit unit = new ProductionUnit();
        unit.setFarm(farm);
        unit.setCode(input.code());
        unit.setType(ProductionUnit.UnitType.valueOf(input.type().toUpperCase()));
        if (input.sizeM3() != null) {
            unit.setSizeM3(BigDecimal.valueOf(input.sizeM3()));
        }
        unit.setWaterSource(input.waterSource());
        unit.setStatus("IDLE");

        return unitRepository.save(unit);
    }
}
