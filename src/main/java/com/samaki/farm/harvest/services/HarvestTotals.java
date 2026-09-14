package com.samaki.farm.harvest.services;

import com.samaki.farm.harvest.entity.HarvestEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * Jumla za mzunguko kutoka kwenye matukio yake - MAHALI PEKEE sheria ya
 * "nini kinahesabiwa wapi" imeandikwa.
 *
 *   harvestedCount = fishCount ya SOLD + REMOVED   (waliotoka WAKIWA HAI)
 *   totalWeightKg  = weightKg  ya SOLD + REMOVED   (null inahesabiwa 0)
 *   mortalityCount = fishCount ya DIED
 *   totalRevenue   = saleAmount ya SOLD
 *
 * DIED haimo kwenye harvestedCount wala uzito KWA MAKUSUDI: kiwango cha
 * kuishi (V19: harvested_count / fingerlings_count) ni samaki WALIOISHI,
 * na mzoga si mavuno.
 *
 * Orodha tupu inatoa sifuri kila mahali - si null. Mzunguko uliofungwa
 * bila tukio lolote (FAILED ya samaki wote kupotea) una jibu HALISI la
 * sifuri, si "haijulikani".
 */
public record HarvestTotals(int harvestedCount, BigDecimal totalWeightKg,
                            int mortalityCount, BigDecimal totalRevenue) {

    public static HarvestTotals of(List<HarvestEvent> events) {
        int harvested = 0;
        int mortality = 0;
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;

        for (HarvestEvent event : events) {
            if (event.getReason().leftAlive()) {
                harvested += event.getFishCount();
                if (event.getWeightKg() != null) {
                    weight = weight.add(event.getWeightKg());
                }
            } else {
                mortality += event.getFishCount();
            }
            if (event.getReason() == HarvestEvent.Reason.SOLD && event.getSaleAmount() != null) {
                revenue = revenue.add(event.getSaleAmount());
            }
        }
        return new HarvestTotals(harvested, weight, mortality, revenue);
    }
}
