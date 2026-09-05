package com.samaki.farm.feed.repository;

import com.samaki.farm.feed.entity.FeedType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedTypeRepository extends JpaRepository<FeedType, Integer> {

    List<FeedType> findAllByOrderByNameAsc();

    /**
     * Zinazotumika pekee. Ni derived query (si findAll + filter ya Java) ili
     * @SQLRestriction ya soft-delete itumike pia - findAll ingeleta hata
     * zilizofutwa kwa njia ya findById (angalia BaseEntity javadoc).
     */
    List<FeedType> findByActiveTrueOrderByNameAsc();

    /** feed_types.name ni UNIQUE (V16), hivyo jina ni kitambulisho salama cha idempotency. */
    Optional<FeedType> findByName(String name);

    /**
     * Je, jina hili tayari limechukuliwa? IKIWEMO na aina zilizofutwa.
     *
     * Ni NATIVE kwa makusudi, kwa sababu ile ile ya
     * RoleRepository.countByNameIncludingDeleted: derived query yoyote
     * inachujwa na @SQLRestriction ya FeedType, hivyo haioni aina
     * iliyofutwa kwa soft-delete. Lakini safu yake BADO IPO kwenye jedwali
     * na `feed_types.name` ni UNIQUE (V16), hivyo kikwazo cha database
     * kinaikataa. Bila swali hili, kusajili upya aina iliyofutwa
     * kungepita ukaguzi wetu na kuangukia DataIntegrityViolationException -
     * yaani CONFLICT yenye sentensi ya jumla kuhusu vikwazo vya database,
     * ambayo haimwelezi msimamizi kwamba tatizo ni jina lililofichwa.
     *
     * `selfId` inaruhusiwa kuwa null (wakati wa kusajili mpya). Ikitolewa,
     * safu yake yenyewe hairuhesabiwi - vinginevyo kuhifadhi aina bila
     * kubadilisha jina lake kungeonekana kama rudufu.
     */
    @Query(value = """
            SELECT COUNT(*) FROM feed_types
            WHERE name = :name
              AND (CAST(:selfId AS INTEGER) IS NULL OR feed_type_id <> CAST(:selfId AS INTEGER))
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("name") String name, @Param("selfId") Integer selfId);
}
