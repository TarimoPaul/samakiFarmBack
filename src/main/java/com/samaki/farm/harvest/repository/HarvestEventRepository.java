package com.samaki.farm.harvest.repository;

import com.samaki.farm.harvest.entity.HarvestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HarvestEventRepository extends JpaRepository<HarvestEvent, Integer> {

    /**
     * Matukio ya mzunguko mmoja, mapya kwanza - swali la orodha
     * (`harvestEvents`) NA la jumla ya closeCycle.
     *
     * Derived query, hivyo @SQLRestriction inachuja yaliyofutwa: tukio
     * lililofutwa HALIMO kwenye jumla ya kufunga, na hiyo ndiyo maana ya
     * kulifuta. Index ya V25 (cycle_id, event_date DESC) inafuata
     * mpangilio huu; harvestEventId inavunja sare kwa matukio ya siku moja.
     */
    List<HarvestEvent> findByCycle_CycleIdOrderByEventDateDescHarvestEventIdDesc(Integer cycleId);

    /**
     * Tukio kwa id, likiwa BADO lipo - si findById, ambayo HAITUMII
     * @SQLRestriction (angalia BaseEntity) na ingeruhusu kufuta
     * lililokwisha futwa.
     */
    Optional<HarvestEvent> findByHarvestEventId(Integer harvestEventId);

    /**
     * Mzunguko wa tukio - KITAMBULISHO PEKEE, bila kupakia tukio wala
     * mzunguko. Ndivyo deleteHarvestEvent inavyoweza kufunga mstari wa
     * mzunguko KABLA ya kuusoma (angalia HarvestService.delete).
     */
    @Query("select e.cycle.cycleId from HarvestEvent e where e.harvestEventId = :harvestEventId")
    Optional<Integer> findCycleIdByHarvestEventId(@Param("harvestEventId") Integer harvestEventId);
}
