package com.samaki.farm.dashboard.repository;

import com.samaki.farm.productionunit.entity.ProductionUnit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * Hali ya shamba KWA TAREHE MOJA.
 *
 * HAKUNA JEDWALI JIPYA LA HISTORIA, na hakuna migration. Kila kitu hapa
 * kinakokotolewa kutoka kwa data iliyopo tayari:
 *
 *   * Kitengo kilikuwepo tarehe hiyo  -> `created_at` / `deleted_at` (BaseEntity,
 *     V2 kwa production_units, V5 kwa farm_users).
 *   * Kitengo kilikuwa ACTIVE         -> kilikuwa na mzunguko unaoendelea.
 *     Hii ni sahihi kwa sababu status ya kitengo INAENDESHWA NA MIZUNGUKO
 *     pekee: CycleService inaiweka "ACTIVE" mzunguko unapoanza (mstari 131) na
 *     "IDLE" mzunguko wa mwisho unapofungwa (releaseUnitIfIdle). Hakuna msimbo
 *     wowote unaoandika "MAINTENANCE", ingawa CHECK ya V1 inairuhusu.
 *   * Mzunguko ulikuwa unaendelea     -> `stocking_date <= tarehe` na
 *     (`actual_harvest_date` haipo AU ni tarehe hiyo au baadaye). closeCycle
 *     DAIMA inaandika actual_harvest_date, hivyo hakuna mzunguko uliofungwa
 *     usio na tarehe.
 *
 * Faida ya kukokotoa badala ya kukusanya: historia inafanya kazi kwa tarehe
 * ZA NYUMA pia, si kuanzia siku tunapowasha kipengele.
 *
 * NATIVE, si JPQL, kwa sababu moja mahususi: @SQLRestriction("is_deleted =
 * false") huchuja rekodi zilizofutwa kwenye KILA query ya JPQL/derived - na
 * kitengo kilichofutwa JANA bado kilikuwepo WIKI ILIYOPITA. Snapshot ya tarehe
 * ya nyuma lazima iwaone. Mizunguko iliyofutwa ni tofauti: kufuta mzunguko
 * kunamaanisha "haukuwahi kuwepo", hivyo `c.is_deleted = false` inabaki.
 */
public interface DashboardDayRepository extends Repository<ProductionUnit, Integer> {

    @Query(value = """
            WITH existing AS (
                SELECT u.unit_id, COALESCE(u.size_m3, 0) AS size_m3
                FROM production_units u
                WHERE u.farm_id = :farmId
                  AND u.created_at::date <= :onDate
                  AND (u.deleted_at IS NULL OR u.deleted_at::date > :onDate)
            ),
            running AS (
                SELECT c.cycle_id, c.unit_id, c.fingerlings_count, c.stocking_date
                FROM cycles c
                WHERE c.is_deleted = false
                  AND c.unit_id IN (SELECT unit_id FROM existing)
                  AND c.stocking_date <= :onDate
                  AND (c.actual_harvest_date IS NULL OR c.actual_harvest_date >= :onDate)
            )
            SELECT
                (SELECT count(*) FROM existing)                      AS units_existing,
                (SELECT count(DISTINCT unit_id) FROM running)        AS units_active,
                (SELECT COALESCE(sum(size_m3), 0) FROM existing)     AS total_volume_m3,
                (SELECT count(*) FROM running)                       AS cycles_running,
                (SELECT COALESCE(sum(fingerlings_count), 0) FROM running)
                                                                     AS fingerlings_running,
                (SELECT COALESCE(sum(fingerlings_count), 0) FROM running
                  WHERE stocking_date = :onDate)                     AS fingerlings_stocked,
                (SELECT count(*) FROM running
                  WHERE stocking_date = :onDate)                     AS cycles_started,
                (SELECT count(*) FROM cycles c
                  WHERE c.is_deleted = false
                    AND c.unit_id IN (SELECT unit_id FROM existing)
                    AND c.actual_harvest_date = :onDate)             AS cycles_closed,
                (SELECT count(*) FROM farm_users fu
                  WHERE fu.farm_id = :farmId
                    AND fu.created_at::date <= :onDate
                    AND (fu.deleted_at IS NULL OR fu.deleted_at::date > :onDate))
                                                                     AS members,
                (SELECT min(u.created_at)::date FROM production_units u
                  WHERE u.farm_id = :farmId)                         AS history_starts_on
            """, nativeQuery = true)
    DashboardDayRow snapshotOn(@Param("farmId") Integer farmId, @Param("onDate") LocalDate onDate);

    /**
     * Vitengo vilivyokuwepo siku hiyo, vikigawanywa kwa AINA.
     *
     * Query ya pili kwa sababu hii inarudisha SAFU NYINGI, moja kwa kila aina
     * iliyokuwepo - na ile ya juu ni safu moja ya jumla. Aina isiyo na kitengo
     * chochote siku hiyo HAIRUDI hapa; frontend inajaza sifuri kutoka kwa
     * orodha yake ya aina, ili kadi isibadilike umbo kulingana na data.
     *
     * `type` ni safu isiyobadilika baada ya kitengo kuundwa - hakuna msimbo
     * unaoiandika tena - hivyo aina ya leo ndiyo iliyokuwa siku ile.
     */
    @Query(value = """
            SELECT u.type AS type, count(*) AS count
            FROM production_units u
            WHERE u.farm_id = :farmId
              AND u.created_at::date <= :onDate
              AND (u.deleted_at IS NULL OR u.deleted_at::date > :onDate)
            GROUP BY u.type
            ORDER BY u.type
            """, nativeQuery = true)
    java.util.List<UnitTypeRow> unitsByTypeOn(@Param("farmId") Integer farmId,
                                              @Param("onDate") LocalDate onDate);

    interface UnitTypeRow {
        String getType();

        long getCount();
    }

    /**
     * Safu moja ya jibu. Interface projection, si record, kwa sababu native
     * query haiwezi kutumia constructor expression ya JPQL.
     */
    interface DashboardDayRow {
        long getUnitsExisting();

        long getUnitsActive();

        java.math.BigDecimal getTotalVolumeM3();

        long getCyclesRunning();

        long getFingerlingsStocked();

        long getCyclesStarted();

        long getCyclesClosed();

        /**
         * Vifaranga walio ndani ya mizunguko ILIYOKUWA INAENDELEA siku hiyo -
         * hifadhi, si mtiririko. Hii ndiyo inayolingana na tile ya dashibodi
         * ya leo; `fingerlingsStocked` hapo chini ni kitu kingine kabisa
         * (walioingizwa SIKU hiyo), na kuzichanganya kungebadilisha maana ya
         * namba bila mtu kujua.
         */
        long getFingerlingsRunning();

        long getMembers();

        /**
         * Tarehe ya kwanza tuliyo na rekodi yake kwa shamba hili.
         *
         * MUHIMU kwa uaminifu wa jibu: V2 iliongeza `created_at` ikiwa na
         * DEFAULT now(), hivyo vitengo vyote vilivyokuwepo KABLA ya migration
         * hiyo vinaonekana kana kwamba viliundwa dakika ile ile. Tarehe kabla
         * ya hii haina jibu la kweli, na UI inapaswa kusema hivyo badala ya
         * kuonyesha sifuri.
         */
        LocalDate getHistoryStartsOn();
    }
}
