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
}
