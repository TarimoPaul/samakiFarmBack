package com.samaki.farm.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Faida ya SHAMBA kwa kipindi - hapa ndipo chakula na gharama za shamba
 * zima zinapoingia.
 *
 * =====================================================================
 * farmRevenue   = SUM(revenue ya mizunguko iliyofungwa kipindini)
 * cycleCosts    = fingerlingCost + cycleOperationalCost (za mizunguko hiyo)
 * farmCosts     = cycleCosts + feedCost + farmOperationalCost
 * farmNetProfit = farmRevenue - farmCosts
 *
 * UANACHAMA WA KIPINDI:
 *   * mzunguko             -> actual_harvest_date
 *   * gharama za mzunguko  -> tarehe ya MAVUNO ya mzunguko wake (si cost_date)
 *   * chakula              -> purchase_date, BILA manunuzi yaliyobatilishwa
 *   * gharama za shamba    -> cost_date
 *
 * capitalTotal (mali, kwa acquired_date) ni KANDO - HAIMO kwenye farmCosts
 * wala farmNetProfit. Mali ni mtaji, si uendeshaji.
 * =====================================================================
 */
public record FarmProfitability(
        Integer farmId,
        String farmName,
        LocalDate fromDate,
        LocalDate toDate,

        BigDecimal farmRevenue,

        BigDecimal fingerlingCost,
        BigDecimal cycleOperationalCost,
        List<CostCategoryAmount> cycleOperationalCostByCategory,
        BigDecimal cycleCosts,

        BigDecimal feedCost,
        int reversedFeedPurchasesExcluded,

        BigDecimal farmOperationalCost,
        List<CostCategoryAmount> farmOperationalCostByCategory,

        BigDecimal farmCosts,
        BigDecimal farmNetProfit,

        int cycleCount,
        int incompleteCycleCount,
        List<CycleProfitability> cycles,

        BigDecimal capitalTotal) {
}
