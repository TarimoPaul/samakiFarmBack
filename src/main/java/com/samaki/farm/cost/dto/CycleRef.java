package com.samaki.farm.cost.dto;

import com.samaki.farm.cycle.entity.Cycle;

/**
 * Mzunguko kama LEBO ya kuchagua, si kama rasilimali kamili.
 *
 * Inatumiwa na pande MBILI za kitu kimoja: `farmCycles` (orodha ya
 * kudondosha ya fomu ya gharama) na `Cost.cycle` (mstari uliokwisha
 * rekodiwa). Ni umbo moja kwa sababu ni swali moja - "ni mzunguko upi?"
 * - na mtumiaji anapaswa kuona maandishi YALE YALE kwenye chaguo na
 * kwenye mstari uliohifadhiwa.
 *
 * LEBO INAJENGWA HAPA, si frontend. Vipande vyake vitatu (tanki, aina ya
 * samaki, hali) vinatoka kwenye majedwali matatu tofauti
 * (`production_units`, `species`, `cycles`), na kila skrini
 * ingelazimika kuviunganisha yenyewe - au kuvipata tofauti.
 */
public record CycleRef(Integer cycleId, String label, String status) {

    /**
     * "T1 - Sato (ACTIVE)".
     *
     * Hali IMO ndani ya lebo kwa makusudi: kichagua kinaonyesha mizunguko
     * iliyofungwa pia (angalia CycleRepository.
     * findByUnit_Farm_FarmIdOrderByStockingDateDescCycleIdDesc), na bila
     * hali mtu asingeweza kutofautisha unaoendelea na uliokwisha vunwa.
     * Inarudishwa PIA kama uga wake ili frontend iweze kupanga au
     * kufifisha bila kuchambua maandishi.
     *
     * Vipande vinavyokosekana havitupi NullPointerException: mzunguko
     * usio na tanki wala aina si hali inayotarajiwa, lakini lebo tupu ni
     * bora kuliko ombi lililoanguka.
     */
    public static CycleRef of(Cycle cycle) {
        if (cycle == null) {
            return null;
        }
        String unit = cycle.getUnit() == null ? "?" : cycle.getUnit().getCode();
        String species = cycle.getSpecies() == null ? "?" : cycle.getSpecies().getName();
        return new CycleRef(cycle.getCycleId(),
                unit + " - " + species + " (" + cycle.getStatus() + ")",
                cycle.getStatus());
    }
}
