package com.samaki.farm.species.repository;

import com.samaki.farm.species.entity.Species;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpeciesRepository extends JpaRepository<Species, Integer> {

    /**
     * Je, jina hili tayari limechukuliwa? IKIWEMO na aina ZILIZOFUTWA.
     *
     * Ni NATIVE kwa makusudi, kwa sababu ile ile ya
     * FeedTypeRepository.countByNameIncludingDeleted: derived query yoyote
     * inachujwa na @SQLRestriction ya Species, hivyo haioni aina
     * iliyofutwa kwa soft-delete. Lakini safu yake BADO IPO kwenye jedwali
     * na `species.name` ni UNIQUE (V1), hivyo kikwazo cha database
     * kinaikataa. Bila swali hili, kusajili upya aina iliyofutwa
     * kungepita ukaguzi wetu na kuangukia DataIntegrityViolationException -
     * yaani hitilafu ya jumla kuhusu vikwazo vya database, ambayo
     * haimwelezi msimamizi kwamba tatizo ni jina lililofichwa.
     *
     * `selfId` inaruhusiwa kuwa null (wakati wa kusajili mpya). Ikitolewa,
     * safu yake yenyewe hairuhesabiwi - ni maandalizi ya updateSpecies,
     * na inaifanya sheria iwe ile ile ya katalogi ya chakula.
     */
    @Query(value = """
            SELECT COUNT(*) FROM species
            WHERE name = :name
              AND (CAST(:selfId AS INTEGER) IS NULL OR species_id <> CAST(:selfId AS INTEGER))
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("name") String name, @Param("selfId") Integer selfId);
}
