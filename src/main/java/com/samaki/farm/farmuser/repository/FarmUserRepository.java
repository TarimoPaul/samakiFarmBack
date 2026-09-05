package com.samaki.farm.farmuser.repository;

import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Watu wangapi wanashikilia nafasi hii - swali la RoleService.deleteRole.
     *
     * Ni derived query, hivyo @SQLRestriction ya FarmUser inaichuja:
     * uanachama ULIOTOLEWA (soft-deleted) hauhesabiwi. Ndivyo
     * inavyopaswa kuwa - mtu aliyeondolewa kwenye shamba hashikilii
     * nafasi yoyote, na kuhesabu safu yake ya zamani kungezuia nafasi
     * isifutike milele kwa sababu isiyoonekana popote kwenye UI.
     */
    long countByRole_RoleId(Integer roleId);

    /**
     * Watu wangapi wako kwenye shamba hili - swali la FarmService.delete.
     *
     * Kama countByRole_RoleId: ni derived query, hivyo uanachama ULIOTOLEWA
     * hauhesabiwi. Aliyekwisha ondolewa hazuii shamba lisifutwe.
     */
    long countByFarm_FarmId(Integer farmId);

    /**
     * WATU wa shamba moja wenye ruhusa fulani - njia ya Reminders ya
     * kujua nani wa kumkumbusha.
     *
     * NI RUHUSA, SI ROLE, na si assignee. `daily_tasks.assigned_role_id`
     * ni NULL kwenye kila kazi inayozalishwa (CycleService.createDefaultTasks
     * hairuweki), hivyo hakuna assignee wa kumfuata. Na kutaja role kwa
     * jina ("WORKER") kungevunjika siku role zinapohaririwa - ilhali
     * ruhusa ndiyo iliyofungamana na maana: mwenye `mark_task_done` ndiye
     * anayeweza kuifunga kazi, hivyo ndiye anayefaa kukumbushwa.
     * Ni sheria ile ile DailyTaskService.complete inayotumia.
     *
     * Uanachama BILA role haupati chochote: `join fu.role` ni inner join,
     * hivyo mtu asiye na role haingii kabisa (angalia FarmUser - role
     * inaruhusiwa kuwa null kwa makusudi).
     *
     * ACTIVE PEKEE. Aliyezuiwa (DISABLED) au ambaye bado hajaidhinishwa
     * (PENDING_APPROVAL) hawezi kuingia kwenye mfumo hata siku hiyo,
     * hivyo kumtumia SMS ya kazi kungekuwa kumtuma mahali asipoweza
     * kufika - na kwa akaunti iliyozuiwa, ni kumpa taarifa za shamba
     * ambazo tayari ameondolewa kwazo.
     *
     * ROOT hayumo, na hiyo ni sahihi: hana uanachama wowote (angalia
     * PermissionChecker), hivyo si mtu wa shamba lolote.
     */
    @Query("""
            select distinct fu.user from FarmUser fu
            join fu.role r
            join r.permissions p
            where fu.farm.farmId = :farmId
              and p.code = :permissionCode
              and fu.user.status = :status
            order by fu.user.name asc
            """)
    List<User> findMembersWithPermission(@Param("farmId") Integer farmId,
                                         @Param("permissionCode") String permissionCode,
                                         @Param("status") UserStatus status);
}
