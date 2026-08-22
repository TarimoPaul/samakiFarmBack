package com.samaki.farm.waterquality.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.waterquality.dto.LogWaterQualityInput;
import com.samaki.farm.waterquality.entity.WaterQualityLog;
import com.samaki.farm.waterquality.repository.WaterQualityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class WaterQualityService {

    private final WaterQualityLogRepository logRepository;
    private final ProductionUnitRepository unitRepository;
    private final FarmUserRepository farmUserRepository;
    private final PermissionChecker permissionChecker;

    public WaterQualityService(WaterQualityLogRepository logRepository, ProductionUnitRepository unitRepository,
                                FarmUserRepository farmUserRepository, PermissionChecker permissionChecker) {
        this.logRepository = logRepository;
        this.unitRepository = unitRepository;
        this.farmUserRepository = farmUserRepository;
        this.permissionChecker = permissionChecker;
    }

    /** unitId ikitolewa: vipimo vya tanki moja; vinginevyo vya shamba zima. */
    @Transactional(readOnly = true)
    public List<WaterQualityLog> listLogs(Integer unitId) {
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        if (unitId != null) {
            requireUnitInCallersFarm(unitId);
            return logRepository.findByUnit_UnitIdOrderByLogDateDesc(unitId);
        }
        return logRepository.findByUnit_Farm_FarmIdOrderByLogDateDesc(user.getFarmId());
    }

    @Transactional
    public WaterQualityLog log(LogWaterQualityInput input) {
        permissionChecker.require("log_water_quality");
        ProductionUnit unit = requireUnitInCallersFarm(input.unitId());

        WaterQualityLog log = new WaterQualityLog();
        log.setUnit(unit);
        log.setLogDate(input.logDate() == null ? LocalDate.now() : LocalDate.parse(input.logDate()));
        log.setPh(toDecimal(input.ph()));
        log.setTemperature(toDecimal(input.temperature()));
        log.setOxygen(toDecimal(input.oxygen()));
        log.setNotes(input.notes());
        log.setRecordedBy(currentFarmUser());

        return logRepository.save(log);
    }

    private ProductionUnit requireUnitInCallersFarm(Integer unitId) {
        ProductionUnit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Tanki/bwawa halijulikani"));
        permissionChecker.requireSameFarm(unit.getFarm().getFarmId());
        return unit;
    }

    private FarmUser currentFarmUser() {
        return farmUserRepository.findByUserId(permissionChecker.currentUser().getUserId()).orElse(null);
    }

    private static BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
