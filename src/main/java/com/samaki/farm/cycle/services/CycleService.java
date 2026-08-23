package com.samaki.farm.cycle.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.cycle.dto.CreateCycleInput;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * FR-3.2 (kukokotoa expected_harvest_date kiotomatiki) + FR-4.1 (kuzalisha
 * daily_tasks kiotomatiki) - tafsiri ya Java ya kile kilichokuwa
 * cycles.routes.js kwenye toleo la Node (sasa halitumiki tena).
 */
@Service
public class CycleService {

    private final CycleRepository cycleRepository;
    private final ProductionUnitRepository unitRepository;
    private final SpeciesRepository speciesRepository;
    private final DailyTaskRepository dailyTaskRepository;
    private final PermissionChecker permissionChecker;

    public CycleService(CycleRepository cycleRepository, ProductionUnitRepository unitRepository,
                         SpeciesRepository speciesRepository, DailyTaskRepository dailyTaskRepository,
                         PermissionChecker permissionChecker) {
        this.cycleRepository = cycleRepository;
        this.unitRepository = unitRepository;
        this.speciesRepository = speciesRepository;
        this.dailyTaskRepository = dailyTaskRepository;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public List<Cycle> listForCurrentFarm(String status) {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        if (status != null && !status.isBlank()) {
            return cycleRepository.findByUnit_Farm_FarmIdAndStatus(farmId, status);
        }
        return cycleRepository.findByUnit_Farm_FarmId(farmId);
    }

    @Transactional
    public Cycle create(CreateCycleInput input) {
        permissionChecker.requireFarmScope("edit_cycle");

        ProductionUnit unit = unitRepository.findById(input.unitId())
                .orElseThrow(() -> new IllegalArgumentException("Tanki/bwawa halijulikani"));
        // unitId inatoka kwa mteja, hivyo LAZIMA ithibitishwe: bila mstari
        // huu mtu mwenye 'edit_cycle' angeweza kuunda mzunguko ndani ya
        // tanki la shamba lingine (D-1).
        permissionChecker.requireResourceInCallersFarm(unit.getFarm().getFarmId());

        // speciesId HAIKAGULIWI kwa shamba kwa MAKUSUDI: `species` ni
        // katalogi ya kimfumo inayoshirikiwa na mashamba yote (Sato,
        // Kambale...), si data ya shamba fulani - haina farm_id kabisa
        // (angalia V1__init_schema.sql).
        Species species = speciesRepository.findById(input.speciesId())
                .orElseThrow(() -> new IllegalArgumentException("Aina ya samaki haijulikani"));

        LocalDate stockingDate = LocalDate.parse(input.stockingDate());
        // FR-3.2: kukokotoa tarehe ya mavuno kiotomatiki kutoka growth_months_avg
        LocalDate expectedHarvest = expectedHarvestDate(stockingDate, species.getGrowthMonthsAvg());

        Cycle cycle = new Cycle();
        cycle.setUnit(unit);
        cycle.setSpecies(species);
        cycle.setStockingDate(stockingDate);
        cycle.setFingerlingsCount(input.fingerlingsCount());
        if (input.survivalRateEstimate() != null) {
            cycle.setSurvivalRateEstimate(BigDecimal.valueOf(input.survivalRateEstimate()));
        }
        cycle.setExpectedHarvestDate(expectedHarvest);
        cycle.setStatus("ACTIVE");
        cycle = cycleRepository.save(cycle);

        unit.setStatus("ACTIVE");
        unitRepository.save(unit);

        // FR-4.1: kuzalisha kazi za kawaida za kila siku kiotomatiki
        createDefaultTasks(cycle);

        return cycle;
    }

    /**
     * FR-3.2 - stockingDate + growth_months_avg.
     *
     * growth_months_avg ni NUMERIC(4,1), yaani nusu-mwezi ni thamani
     * halali. Awali hapa palikuwa na .longValue() ambayo INAKATA sehemu ya
     * desimali: aina ya miezi 6.5 ilikokotolewa kama 6 - wiki mbili
     * mapema, kimyakimya (angalia FRONTEND_BACKEND_AUDIT.md, D-7).
     *
     * Miezi mizima inaongezwa kama miezi (hivyo tarehe ya mwezi
     * inahifadhiwa), na sehemu ya desimali inageuzwa kuwa SIKU za mwezi
     * halisi inamoangukia - si wastani wa siku 30 - ili nusu ya Februari
     * isihesabiwe sawa na nusu ya Julai.
     */
    static LocalDate expectedHarvestDate(LocalDate stockingDate, BigDecimal growthMonthsAvg) {
        long wholeMonths = growthMonthsAvg.longValue();
        LocalDate date = stockingDate.plusMonths(wholeMonths);

        BigDecimal fraction = growthMonthsAvg.subtract(BigDecimal.valueOf(wholeMonths));
        if (fraction.signum() <= 0) {
            return date;
        }

        long extraDays = fraction.multiply(BigDecimal.valueOf(date.lengthOfMonth()))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return date.plusDays(extraDays);
    }

    private void createDefaultTasks(Cycle cycle) {
        record DefaultTask(String type, LocalTime time) {}
        List<DefaultTask> defaults = List.of(
                new DefaultTask("Kulisha - Asubuhi", LocalTime.of(7, 0)),
                new DefaultTask("Kulisha - Jioni", LocalTime.of(17, 0)),
                new DefaultTask("Kuangalia Maji", LocalTime.of(8, 0))
        );
        for (DefaultTask dt : defaults) {
            DailyTask task = new DailyTask();
            task.setCycle(cycle);
            task.setTaskType(dt.type());
            task.setScheduledTime(dt.time());
            task.setFrequency("DAILY");
            dailyTaskRepository.save(task);
        }
    }
}
