package com.samaki.farm.dashboard.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.dashboard.dto.DashboardDay;
import com.samaki.farm.dashboard.dto.UnitTypeCount;
import com.samaki.farm.dashboard.repository.DashboardDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class DashboardDayService {

    private final DashboardDayRepository dashboardDayRepository;
    private final PermissionChecker permissionChecker;

    public DashboardDayService(DashboardDayRepository dashboardDayRepository,
                               PermissionChecker permissionChecker) {
        this.dashboardDayRepository = dashboardDayRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * `view_dashboard` + muktadha wa shamba - ruhusa ile ile ambayo dashibodi
     * ya leo inatumia. Kuiona hali ya jana si uwezo mpya; ni swali lile lile
     * kwa tarehe tofauti.
     */
    @Transactional(readOnly = true)
    public DashboardDay onDate(String rawDate) {
        Integer farmId = permissionChecker.requireFarmScope("view_dashboard");
        LocalDate date = parseDate(rawDate);

        DashboardDayRepository.DashboardDayRow row = dashboardDayRepository.snapshotOn(farmId, date);

        long existing = row == null ? 0 : row.getUnitsExisting();
        long active = row == null ? 0 : row.getUnitsActive();
        LocalDate historyStartsOn = row == null ? null : row.getHistoryStartsOn();

        return new DashboardDay(
                date.toString(),
                existing,
                active,
                // IDLE ni "kilichobaki", si safu yake: hakuna msimbo unaoandika
                // MAINTENANCE, hivyo kila kitengo kisicho na mzunguko siku hiyo
                // kilikuwa IDLE. Ikija siku MAINTENANCE ikaandikwa, hapa ndipo
                // itabidi igawanywe.
                existing - active,
                row == null || row.getTotalVolumeM3() == null ? 0d : row.getTotalVolumeM3().doubleValue(),
                row == null ? 0 : row.getCyclesRunning(),
                row == null ? 0 : row.getCyclesStarted(),
                row == null ? 0 : row.getCyclesClosed(),
                row == null ? 0 : row.getFingerlingsRunning(),
                row == null ? 0 : row.getFingerlingsStocked(),
                row == null ? 0 : row.getMembers(),
                dashboardDayRepository.unitsByTypeOn(farmId, date).stream()
                        .map(unitType -> new UnitTypeCount(unitType.getType(), unitType.getCount()))
                        .toList(),
                historyStartsOn,
                // Tarehe iliyo KABLA ya rekodi ya kwanza haijibiki. Jibu bado
                // linarudi - sifuri kila mahali - lakini likiwa limetiwa alama
                // ya "si kamili", ili UI iseme "hakuna rekodi ya siku hiyo"
                // badala ya kudai shamba lilikuwa tupu.
                historyStartsOn != null && !date.isBefore(historyStartsOn));
    }

    /**
     * Tarehe za GraphQL kwenye schema hii ni String (angalia `dailyTasks`),
     * hivyo ukaguzi ni wa hapa. Ujumbe unataja muundo unaokubalika badala ya
     * kuacha DateTimeParseException ya Java ielekee kwa mteja.
     */
    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tarehe inahitajika (mfano 2026-09-07).");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException badFormat) {
            throw new IllegalArgumentException("Tarehe si sahihi. Tumia muundo 2026-09-07.");
        }
    }
}
