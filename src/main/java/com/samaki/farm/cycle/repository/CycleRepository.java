package com.samaki.farm.cycle.repository;

import com.samaki.farm.cycle.entity.Cycle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CycleRepository extends JpaRepository<Cycle, Integer> {
    List<Cycle> findByUnit_Farm_FarmId(Integer farmId);
    List<Cycle> findByUnit_Farm_FarmIdAndStatus(Integer farmId, String status);

    /**
     * Mizunguko YOTE ya shamba moja - INAYOENDELEA NA ILIYOFUNGWA - mpya
     * kwanza. Swali la kichagua-mzunguko cha fomu ya gharama
     * (Query.farmCycles).
     *
     * HAKUNA kuchuja kwa `status` hapa kwa makusudi, tofauti na
     * findByUnit_Farm_FarmIdAndStatus hapo juu: gharama inarekodiwa
     * MARA NYINGI baada ya mzunguko kufungwa (bili ya umeme ya Machi
     * inalipwa Aprili), na kichagua kisichoonyesha mizunguko iliyofungwa
     * kingelazimisha gharama hiyo kuwa ya shamba zima - au kupotea.
     *
     * @EntityGraph: lebo ya kila chaguo ni unit.code + species.name +
     * status, hivyo bila hii ni N+1 ya maswali mawili kwa kila mzunguko.
     *
     * Mpangilio: uliowekwa karibuni kwanza, na cycleId inavunja sare -
     * ndio unaotafutwa kwenye orodha ya kudondosha.
     */
    @EntityGraph(attributePaths = {"unit", "species"})
    List<Cycle> findByUnit_Farm_FarmIdOrderByStockingDateDescCycleIdDesc(Integer farmId);

    /**
     * Mzunguko kwa id, ukiwa BADO upo.
     *
     * Derived query, si findById: @SQLRestriction inatumika hapo pekee
     * (angalia BaseEntity), hivyo findById inarudisha hata mzunguko
     * uliofutwa - ndiyo maana CycleService.requireCycleInCallersFarm
     * inalazimika kukagua isDeleted kwa mkono baada yake.
     */
    @EntityGraph(attributePaths = {"unit", "species"})
    Optional<Cycle> findByCycleId(Integer cycleId);

    /**
     * Mzunguko kwa id, ukiwa BADO upo, NA MSTARI WAKE UKIFUNGWA
     * (SELECT ... FOR UPDATE) hadi transaction iishe.
     *
     * Ndio mlango wa kila operesheni inayobadilisha MAVUNO: closeCycle,
     * recordHarvestEvent, deleteHarvestEvent. Bila kufuli, ombi la kurekodi
     * tukio linaloingia sambamba na kufunga lingeweza kuona mzunguko
     * ACTIVE, kisha kuandika tukio BAADA ya closeCycle kukokotoa jumla -
     * tukio lililopo kwenye mzunguko uliofungwa bila kuwa kwenye jumla
     * zake. Kufuli moja kwenye mstari wa mzunguko inazipanga zote tatu
     * mstari mmoja.
     *
     * HAKUNA @EntityGraph kwa makusudi: graph ingeongeza LEFT JOIN, na
     * PostgreSQL inakataa FOR UPDATE upande wa nullable wa outer join.
     * unit/species ni EAGER hata hivyo - zinasomwa kwa select zao.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cycle> findForUpdateByCycleId(Integer cycleId);

    /**
     * Mizunguko ILIYOFUNGWA ya shamba moja ndani ya kipindi, kwa
     * actual_harvest_date (mipaka yote miwili imo) - swali la
     * ProfitabilityService.farmProfitability. `statuses` ni HARVESTED +
     * FAILED: mzunguko unaoendelea hauna mapato ya mwisho, hivyo hauingii
     * kipindi chochote hadi ufungwe.
     */
    @EntityGraph(attributePaths = {"unit", "species"})
    List<Cycle> findByUnit_Farm_FarmIdAndStatusInAndActualHarvestDateBetweenOrderByActualHarvestDateAscCycleIdAsc(
            Integer farmId, Collection<String> statuses, LocalDate fromDate, LocalDate toDate);
}
