package com.samaki.farm.finance.dto;

import com.samaki.farm.cost.dto.CycleRef;
import com.samaki.farm.cycle.entity.Cycle;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Faida ya MZUNGUKO MMOJA - "faida kabla ya chakula na gharama za shamba
 * zima" (Method A).
 *
 * =====================================================================
 * cycleNetProfit = revenue - fingerlingCost - cycleOperationalCost
 *
 * CHAKULA HAKIMO, na hilo si upungufu: `feed_purchases` haina cycle_id,
 * hivyo gharama ya chakula haiwezi kugawiwa mzunguko bila kubuni. Chakula
 * na gharama za shamba zima zinaingia kwenye FarmProfitability pekee.
 *
 * MZUNGUKO UNAOENDELEA (ACTIVE): revenue na cycleNetProfit ni NULL - hauna
 * mapato ya mwisho, na namba ya nusu ingesomeka kama faida halisi.
 * Gharama zilizokwisha rekodiwa zinarudishwa, kwa sababu ni ukweli.
 *
 * NULL YA DATABASE NI SIFURI KWENYE HESABU, LAKINI HAIFICHWI:
 * total_revenue ni NULL kwa mizunguko iliyofungwa kabla ya V25, na
 * fingerling_cost ni NULL isiporekodiwa. Zinahesabiwa kama 0, na
 * revenueRecorded / fingerlingCostRecorded zinasema hivyo waziwazi.
 *
 * MAPATO NI `cycles.total_revenue` ILIYOHIFADHIWA, si jumla ya matukio:
 * kwa mzunguko wa kabla ya V25 jumla ya matukio ingekuwa 0 ya uongo.
 * =====================================================================
 */
public record CycleProfitability(
        Integer cycleId,
        String label,
        String status,
        LocalDate actualHarvestDate,
        BigDecimal revenue,
        boolean revenueRecorded,
        BigDecimal fingerlingCost,
        boolean fingerlingCostRecorded,
        BigDecimal cycleOperationalCost,
        BigDecimal cycleNetProfit) {

    public static CycleProfitability of(Cycle cycle, BigDecimal cycleOperationalCost) {
        boolean closed = isClosed(cycle);
        boolean revenueRecorded = cycle.getTotalRevenue() != null;
        boolean fingerlingCostRecorded = cycle.getFingerlingCost() != null;

        BigDecimal fingerlingCost = fingerlingCostRecorded ? cycle.getFingerlingCost() : BigDecimal.ZERO;
        BigDecimal revenue = !closed ? null
                : revenueRecorded ? cycle.getTotalRevenue() : BigDecimal.ZERO;
        BigDecimal netProfit = !closed ? null
                : revenue.subtract(fingerlingCost).subtract(cycleOperationalCost);

        return new CycleProfitability(
                cycle.getCycleId(),
                CycleRef.of(cycle).label(),
                cycle.getStatus(),
                cycle.getActualHarvestDate(),
                revenue,
                revenueRecorded,
                fingerlingCost,
                fingerlingCostRecorded,
                cycleOperationalCost,
                netProfit);
    }

    public static boolean isClosed(Cycle cycle) {
        return Cycle.HARVESTED.equals(cycle.getStatus()) || Cycle.FAILED.equals(cycle.getStatus());
    }

    /** Mapato au gharama ya vifaranga haikurekodiwa - imehesabiwa kama 0. */
    public boolean incomplete() {
        return !revenueRecorded || !fingerlingCostRecorded;
    }
}
