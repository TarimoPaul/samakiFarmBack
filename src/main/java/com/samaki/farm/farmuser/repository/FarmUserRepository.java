package com.samaki.farm.farmuser.repository;

import com.samaki.farm.farmuser.entity.FarmUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmUserRepository extends JpaRepository<FarmUser, UUID> {

    // farm/role/role.permissions ni LAZY - zinahitaji fetch join hapa hapa
    // kwa sababu JwtAuthFilter (Servlet Filter) inazisoma NJE ya Hibernate
    // session: OpenEntityManagerInViewInterceptor (open-in-view) ni
    // HandlerInterceptor inayofunguliwa ndani ya DispatcherServlet, BAADA ya
    // Filters (JwtAuthFilter) kuisha kupita - hivyo lazy load ingetupa
    // LazyInitializationException isiyoonekana (imekamatwa kimya na
    // JwtAuthFilter's catch-all), na mtumiaji angeonekana "hajaingia" licha
    // ya token sahihi.
    //
    // Ni derived query (si findById), hivyo @SQLRestriction ya FarmUser
    // inatumika: mtumiaji aliyefutwa (soft-delete) hapati authentication.
    @EntityGraph(attributePaths = {"farm", "role.permissions"})
    Optional<FarmUser> findByUserId(UUID userId);

    // Login: farm/role zinahitajika mara moja kujenga token + jibu la login.
    @EntityGraph(attributePaths = {"farm", "role"})
    Optional<FarmUser> findByPhone(String phone);

    @EntityGraph(attributePaths = {"farm", "role"})
    Optional<FarmUser> findByEmail(String email);

    /** Watumiaji wote wa shamba fulani - iliwezekana tu baada ya kuunganisha entity mbili. */
    @EntityGraph(attributePaths = {"role"})
    List<FarmUser> findByFarm_FarmId(Integer farmId);

    // ONYO: hizi zinachujwa na @SQLRestriction, hivyo HAZIONI mtumiaji
    // aliyefutwa. Namba/email yake bado inashikilia UNIQUE constraint ya
    // database - hivyo kusajili tena namba ile ile kutashindwa kwenye DB
    // (409 kupitia DataIntegrityViolationException) badala ya hapa.
    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
