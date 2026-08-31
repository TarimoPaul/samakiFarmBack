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
}
