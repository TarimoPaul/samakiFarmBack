package com.samaki.farm.cost.repository;

import com.samaki.farm.cost.entity.CostCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CostCategoryRepository extends JpaRepository<CostCategory, Integer> {

    /** Katalogi kwa mpangilio wa kusomeka - ndivyo dropdown inavyoihitaji. */
    List<CostCategory> findAllByOrderByNameAsc();

    /**
     * Aina hii, ikiwa BADO ipo - findById peke yake hairuhesabu
     * @SQLRestriction (angalia BaseEntity), hivyo ingerudisha hata aina
     * iliyofutwa na gharama mpya ingeweza kuielekea.
     */
    Optional<CostCategory> findByCostCategoryId(Integer costCategoryId);

    /**
     * Je, jina hili tayari limechukuliwa? IKIWEMO na aina ZILIZOFUTWA.
     *
     * Ni NATIVE kwa sababu ile ile ya AssetCategoryRepository (na
     * SpeciesRepository na FeedTypeRepository): derived query yoyote
     * inachujwa na @SQLRestriction ya CostCategory, hivyo haioni aina
     * iliyofutwa kwa soft-delete. Lakini safu yake BADO IPO kwenye
     * jedwali na `cost_categories.name` ni UNIQUE ya kawaida (V23), hivyo
     * kikwazo cha database kinaikataa. Bila swali hili, kusajili upya
     * aina iliyofutwa kungepita ukaguzi wetu na kuangukia
     * DataIntegrityViolationException - CONFLICT yenye sentensi ya jumla
     * kuhusu vikwazo vya database, isiyomweleza msimamizi kwamba tatizo
     * ni jina ASILOLIONA.
     *
     * `selfId` inaruhusiwa kuwa null (wakati wa kusajili mpya). Ikitolewa,
     * safu yake yenyewe hairuhesabiwi - ni maandalizi ya kuhariri.
     */
    @Query(value = """
            SELECT COUNT(*) FROM cost_categories
            WHERE name = :name
              AND (CAST(:selfId AS INTEGER) IS NULL OR cost_category_id <> CAST(:selfId AS INTEGER))
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("name") String name, @Param("selfId") Integer selfId);
}
