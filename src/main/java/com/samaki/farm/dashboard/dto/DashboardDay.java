package com.samaki.farm.dashboard.dto;

import java.time.LocalDate;

/**
 * Hali ya shamba tarehe moja mahususi - jibu la `dashboardOnDate`.
 *
 * `historyStartsOn` na `historyComplete` ni sehemu ya jibu, si nyongeza:
 * snapshot ya tarehe iliyo kabla ya rekodi zetu si sifuri, ni "hatujui", na
 * UI haiwezi kutofautisha hizo mbili bila kuambiwa.
 */
public record DashboardDay(
        String date,
        long unitsExisting,
        long unitsActive,
        long unitsIdle,
        double totalVolumeM3,
        long cyclesRunning,
        long cyclesStarted,
        long cyclesClosed,
        long fingerlingsRunning,
        long fingerlingsStocked,
        long members,
        /**
         * Vitengo kwa aina. Aina isiyo na kitengo siku hiyo haipo kwenye
         * orodha - mteja anajaza sifuri kutoka kwa orodha yake ya aina.
         */
        java.util.List<UnitTypeCount> unitsByType,
        LocalDate historyStartsOn,
        boolean historyComplete) {
}
