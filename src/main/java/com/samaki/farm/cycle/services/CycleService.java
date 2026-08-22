package com.samaki.farm.cycle.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
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
        AuthenticatedUser user = permissionChecker.require("view_dashboard");
        if (status != null && !status.isBlank()) {
            return cycleRepository.findByUnit_Farm_FarmIdAndStatus(user.getFarmId(), status);
        }
        return cycleRepository.findByUnit_Farm_FarmId(user.getFarmId());
    }

    @Transactional
    public Cycle create(CreateCycleInput input) {
        permissionChecker.require("edit_cycle");

        ProductionUnit unit = unitRepository.findById(input.unitId())
                .orElseThrow(() -> new IllegalArgumentException("Tanki/bwawa halijulikani"));
        Species species = speciesRepository.findById(input.speciesId())
                .orElseThrow(() -> new IllegalArgumentException("Aina ya samaki haijulikani"));

        LocalDate stockingDate = LocalDate.parse(input.stockingDate());
        // FR-3.2: kukokotoa tarehe ya mavuno kiotomatiki kutoka growth_months_avg
        LocalDate expectedHarvest = stockingDate.plusMonths(species.getGrowthMonthsAvg().longValue());

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
