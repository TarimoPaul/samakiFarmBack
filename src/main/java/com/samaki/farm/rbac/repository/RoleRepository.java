package com.samaki.farm.rbac.repository;

import com.samaki.farm.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /**
     * Je, jina hili tayari limechukuliwa? IKIWEMO na nafasi zilizofutwa.
     *
     * Ni NATIVE kwa makusudi. `existsByName` ni derived query, hivyo
     * @SQLRestriction inaichuja - haioni nafasi iliyofutwa kwa
     * soft-delete. Lakini safu yake bado ipo kwenye jedwali, na
     * `roles.name` ni UNIQUE, hivyo kikwazo cha database bado kinakataa.
     * Bila swali hili, kutengeneza upya nafasi iliyofutwa kungepita
     * ukaguzi wetu na kuangukia DataIntegrityViolationException - yaani
     * 409 yenye sentensi ya jumla kuhusu "vikwazo vya database", ambayo
     * haimwelezi msimamizi lolote analoweza kulifanyia kazi.
     *
     * `selfId` inaruhusiwa kuwa null (wakati wa kutengeneza mpya).
     * Ikitolewa, safu yake yenyewe hairuhesabiwi - vinginevyo kuhifadhi
     * nafasi bila kubadilisha jina lake kungeonekana kama rudufu.
     */
    @Query(value = """
            SELECT COUNT(*) FROM roles
            WHERE name = :name
              AND (CAST(:selfId AS INTEGER) IS NULL OR role_id <> CAST(:selfId AS INTEGER))
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("name") String name, @Param("selfId") Integer selfId);
}
