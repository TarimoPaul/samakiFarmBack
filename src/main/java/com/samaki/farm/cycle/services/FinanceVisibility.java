package com.samaki.farm.cycle.services;

import com.samaki.farm.auth.security.PermissionChecker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fedha za uzalishaji - gharama ya vifaranga, mapato ya mzunguko, kiasi
 * cha mauzo - zinarudi null kwa asiye na `view_finance`.
 *
 * =====================================================================
 * KWA NINI KWENYE UGA (field resolver), SI KWENYE QUERY
 *
 * `Cycle` inafikiwa kwa njia NYINGI kwenye schema: `cycles`, `createCycle`,
 * `closeCycle`, na `FeedingLog.cycle` - ile ya mwisho inasomwa na WORKER
 * kila siku. Kuficha ndani ya service ya `cycles` pekee kungeacha njia
 * nyingine zikitangaza namba ile ile. Resolver za uga (CycleResolver,
 * HarvestResolver) zinaita method hii kila mara uga huo unapoombwa,
 * HAIJALISHI njia iliyoufikisha.
 *
 * Kama `view_feed_cost` (angalia FeedService.listPurchases): namba
 * isiyopaswa kumfikia mtu HAIONDOKI SERVER - kuficha kwenye UI pekee
 * kungeiacha kwenye jibu la JSON.
 *
 * ENTITY HAIGUSWI: method inarudisha thamani mpya tu, haiandiki null
 * kwenye entity inayosimamiwa na Hibernate (angalia FeedPurchaseView kwa
 * mtego huo - flush ingefuta fedha database).
 *
 * `view_finance` ipo tangu V1 (OWNER, FARM_MANAGER) na ni ruhusa ya
 * KUONA - ndiyo maana yake halisi: "Kuona gharama/mauzo/faida".
 * =====================================================================
 */
@Component
public class FinanceVisibility {

    public static final String PERMISSION = "view_finance";

    private final PermissionChecker permissionChecker;

    public FinanceVisibility(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    /** Kiasi chenyewe kwa mwenye `view_finance`, null kwa wengine. */
    public BigDecimal visible(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return permissionChecker.has(PERMISSION) ? amount : null;
    }
}
