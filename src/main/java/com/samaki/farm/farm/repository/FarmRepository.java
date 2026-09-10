package com.samaki.farm.farm.repository;

import com.samaki.farm.farm.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmRepository extends JpaRepository<Farm, Integer> {

    /**
     * Je, shamba hili lipo kweli?
     *
     * Ni derived query kwa MAKUSUDI (si existsById): derived queries pekee
     * ndizo zinazopitia @SQLRestriction ya Farm, hivyo shamba lililofutwa
     * (is_deleted = true) linajibiwa "halipo" - ndilo jibu sahihi kwa
     * JwtAuthFilter inayothibitisha shamba alilochagua ROOT.
     */
    boolean existsByFarmId(Integer farmId);

    /**
     * Je, jina hili tayari linatumika na shamba LILILOPO?
     *
     * Derived queries, hivyo @SQLRestriction inazichuja na shamba
     * lililofutwa halihesabiwi - jambo linalolingana KABISA na kikwazo cha
     * database tangu V14, ambacho ni partial index chenye
     * `WHERE is_deleted = false`.
     *
     * Ndiyo maana hapa hakuna swali la native kama lile la
     * RoleRepository/UserRepository: huko jina la kilichofutwa linabaki
     * limechukuliwa (UNIQUE ya kawaida), hapa linaachiwa huru.
     */
    boolean existsByName(String name);

    /** Kama existsByName, lakini shamba lenyewe halijihesabu wakati wa kuhariri. */
    boolean existsByNameAndFarmIdNot(String name, Integer farmId);

    /** Shamba kwa id, likiwa BADO lipo - findById peke yake haiheshimu @SQLRestriction. */
    java.util.Optional<Farm> findByFarmId(Integer farmId);

    /**
     * Mashamba ambayo mtu huyu ni MMILIKI wake (farms.owner_user_id).
     *
     * Ni nusu ya pili ya swali la AssetService.callersFarmIds; nusu ya
     * kwanza ni uanachama (farm_users). Vyote viwili vinahitajika kwa
     * sababu ni dhana MBILI tofauti kwenye schema hii: umiliki ni safu
     * kwenye `farms`, uanachama ni safu kwenye `farm_users`, na
     * FarmService.create inaunda shamba lisilo na mmiliki kabisa. Kwa
     * vitendo mmiliki huwa mwanachama pia (angalia DevSeedService), lakini
     * hakuna kikwazo kinachohakikisha hivyo - na daftari la mali
     * lisingepaswa kumfichia mmiliki shamba lake kwa sababu ya pengo hilo.
     *
     * Ni derived query, hivyo @SQLRestriction ya Farm inaichuja: shamba
     * lililofutwa halirudishwi.
     */
    java.util.List<Farm> findByOwner_UserId(java.util.UUID userId);

    /**
     * Mashamba haya kwa vitambulisho vyao - swali la
     * AssetService.listCallersFarms (Query.myFarms).
     *
     * SI findAllById: ile inapitia EntityManager.find() kwa kila PK, na
     * Hibernate HAITUMII @SQLRestriction kwenye lookup ya moja kwa moja ya
     * PK (angalia BaseEntity) - shamba lililofutwa lingerudi kwenye
     * kichagua-shamba cha frontend. Derived query inachujwa ipasavyo.
     *
     * Kwa vitendo callersFarmIds tayari imechuja yaliyofutwa kwenye vyanzo
     * vyake vyote viwili; hii ni ngome ya pili, sheria ile ile ya
     * AssetService.requireCallersFarm.
     *
     * Mpangilio ni wa farmId kwa makusudi: orodha ya kudondosha
     * inayobadilisha mpangilio kila ombi ni orodha isiyoaminika.
     */
    java.util.List<Farm> findByFarmIdInOrderByFarmIdAsc(java.util.List<Integer> farmIds);
}
