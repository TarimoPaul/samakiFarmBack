package com.samaki.farm.asset.repository;

import com.samaki.farm.asset.entity.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Integer> {

    /** Katalogi kwa mpangilio wa kusomeka - ndivyo dropdown inavyoihitaji. */
    List<AssetCategory> findAllByOrderByNameAsc();

    /**
     * Aina hii, ikiwa BADO ipo - findById peke yake hairuhesabu
     * @SQLRestriction (angalia BaseEntity), hivyo ingerudisha hata aina
     * iliyofutwa na mali mpya ingeweza kuielekea.
     */
    java.util.Optional<AssetCategory> findByAssetCategoryId(Integer assetCategoryId);

    /**
     * Je, jina hili tayari limechukuliwa? IKIWEMO na aina ZILIZOFUTWA.
     *
     * Ni NATIVE kwa sababu ile ile ya SpeciesRepository na
     * FeedTypeRepository: derived query yoyote inachujwa na
     * @SQLRestriction ya AssetCategory, hivyo haioni aina iliyofutwa kwa
     * soft-delete. Lakini safu yake BADO IPO kwenye jedwali na
     * `asset_categories.name` ni UNIQUE ya kawaida (V21), hivyo kikwazo
     * cha database kinaikataa. Bila swali hili, kusajili upya aina
     * iliyofutwa kungepita ukaguzi wetu na kuangukia
     * DataIntegrityViolationException - CONFLICT yenye sentensi ya jumla
     * kuhusu vikwazo vya database, isiyomweleza msimamizi kwamba tatizo
     * ni jina ASILOLIONA.
     *
     * `selfId` inaruhusiwa kuwa null (wakati wa kusajili mpya). Ikitolewa,
     * safu yake yenyewe hairuhesabiwi - ni maandalizi ya kuhariri, na
     * inaifanya sheria iwe ile ile ya katalogi nyingine mbili.
     */
    @Query(value = """
            SELECT COUNT(*) FROM asset_categories
            WHERE name = :name
              AND (CAST(:selfId AS INTEGER) IS NULL OR asset_category_id <> CAST(:selfId AS INTEGER))
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("name") String name, @Param("selfId") Integer selfId);
}
