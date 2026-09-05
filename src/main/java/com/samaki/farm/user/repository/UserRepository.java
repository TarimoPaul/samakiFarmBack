package com.samaki.farm.user.repository;

import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Derived queries (SI findById) - hivyo @SQLRestriction ya User inatumika
    // na mtu aliyefutwa hapatikani kabisa. Angalia BaseEntity kwa maelezo.
    Optional<User> findByUserId(UUID userId);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    /** "Nionyeshe wanaosubiri idhini" - inatumia idx_users_status. */
    List<User> findByStatusOrderByCreatedAtAsc(UserStatus status);

    /**
     * Je, namba hii tayari imechukuliwa? IKIWEMO na waliofutwa.
     *
     * NI NATIVE kwa makusudi, na hapo ndipo tofauti yake muhimu na
     * `existsByPhone` hapo juu ilipo. existsByPhone ni derived query, hivyo
     * @SQLRestriction inaichuja - HAIMWONI mtu aliyefutwa kwa soft-delete.
     * Lakini safu yake bado ipo kwenye jedwali na `users.phone` ni UNIQUE,
     * hivyo kikwazo cha database bado kinakataa. Bila swali hili, kutumia
     * tena namba ya mtu aliyefutwa kungepita ukaguzi wetu na kuangukia
     * DataIntegrityViolationException - 409 yenye sentensi ya jumla kuhusu
     * "vikwazo vya database", isiyomwambia msimamizi kwamba tatizo ni namba
     * ya simu tu.
     *
     * `selfId` inaruhusiwa kuwa null (wakati wa kuunda mtu mpya). Ikitolewa,
     * safu yake yenyewe hairuhesabiwi - vinginevyo kuhifadhi mtu bila
     * kubadilisha namba yake kungeonekana kama rudufu.
     */
    @Query(value = """
            SELECT COUNT(*) FROM users
            WHERE phone = :phone
              AND (CAST(:selfId AS UUID) IS NULL OR user_id <> CAST(:selfId AS UUID))
            """, nativeQuery = true)
    long countByPhoneIncludingDeleted(@Param("phone") String phone, @Param("selfId") UUID selfId);

    /** Kama countByPhoneIncludingDeleted, kwa `users.email` (nayo ni UNIQUE). */
    @Query(value = """
            SELECT COUNT(*) FROM users
            WHERE email = :email
              AND (CAST(:selfId AS UUID) IS NULL OR user_id <> CAST(:selfId AS UUID))
            """, nativeQuery = true)
    long countByEmailIncludingDeleted(@Param("email") String email, @Param("selfId") UUID selfId);
}
