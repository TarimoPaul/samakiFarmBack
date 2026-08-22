package com.samaki.farm.farmuser.repository;

import com.samaki.farm.farmuser.entity.FarmUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmUserRepository extends JpaRepository<FarmUser, FarmUser.FarmUserId> {

    // farm/role/role.permissions ni LAZY - zinahitaji fetch join hapa hapa
    // kwa sababu JwtAuthFilter (Servlet Filter) inazisoma NJE ya Hibernate
    // session: open-in-view ni HandlerInterceptor inayofunguliwa ndani ya
    // DispatcherServlet, BAADA ya Filters kuisha kupita - hivyo lazy load
    // ingetupa LazyInitializationException isiyoonekana (imekamatwa kimya na
    // JwtAuthFilter's catch-all), na mtumiaji angeonekana "hajaingia" licha
    // ya token sahihi.
    //
    // Inarudisha List: mtu anaweza kuwa na uanachama zaidi ya mmoja.
    // Kupanga kwa farmId kunahakikisha "shamba la kwanza" ni thabiti kila
    // wakati (angalia JwtAuthFilter / AuthService - TODO: farm switching).
    @EntityGraph(attributePaths = {"farm", "role.permissions"})
    List<FarmUser> findByUser_UserIdOrderByFarm_FarmIdAsc(UUID userId);

    @EntityGraph(attributePaths = {"user", "role"})
    List<FarmUser> findByFarm_FarmIdOrderByUser_NameAsc(Integer farmId);

    @EntityGraph(attributePaths = {"user", "farm", "role"})
    Optional<FarmUser> findByUser_UserIdAndFarm_FarmId(UUID userId, Integer farmId);

    boolean existsByUser_UserIdAndFarm_FarmId(UUID userId, Integer farmId);
}
