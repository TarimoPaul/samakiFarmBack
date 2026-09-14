package com.samaki.farm.farmuser.repository;

import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * VITAMBULISHO vya mashamba yote ambayo mtu huyu ni mwanachama wake.
     *
     * Ni swali la AssetService.callersFarmIds: daftari la mali ni la
     * KAMPUNI, hivyo linahitaji mashamba YOTE ya mwombaji - si `farmId`
     * moja iliyo kwenye principal. Kumbuka JwtAuthFilter inachukua
     * uanachama wa KWANZA pekee (`memberships.get(0)`, ikiwa na TODO ya
     * farm switching), hivyo principal HAIWEZI kujibu swali hili;
     * database ndiyo inayoweza.
     *
     * `join fu.farm f` ni ya WAZI kwa makusudi, si `fu.farm.farmId`.
     * Njia fupi ingesoma safu ya FK bila kugusa jedwali la `farms` hata
     * kidogo, hivyo @SQLRestriction ya Farm isingetumika na shamba
     * LILILOFUTWA lingeingia kwenye orodha - mali zake zikionekana
     * kwenye daftari la mtu ambaye shamba lake halipo tena.
     *
     * Inarudisha id pekee (si FarmUser): mtumiaji anahitaji seti ya
     * vitambulisho, na findByUser_UserIdOrderByFarm_FarmIdAsc hapo juu
     * inavuta `role.permissions` kwa ajili ya JwtAuthFilter - mzigo
     * usiohitajika hapa.
     */
    @Query("""
            select f.farmId from FarmUser fu
            join fu.farm f
            where fu.user.userId = :userId
            order by f.farmId asc
            """)
    List<Integer> findFarmIdsByUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"user", "farm", "role"})
    Optional<FarmUser> findByUser_UserIdAndFarm_FarmId(UUID userId, Integer farmId);

    boolean existsByUser_UserIdAndFarm_FarmId(UUID userId, Integer farmId);

    /**
     * Inarudisha uanachama ULIOTOLEWA (soft-deleted), badala ya kuingiza mpya.
     *
     * PK ni (user_id, farm_id), na kumtoa mtu kunaacha safu yake ikiwa na
     * is_deleted = true. existsBy... hapo juu HAIIONI (@SQLRestriction), hivyo
     * assignMembership ilikuwa inajaribu INSERT na kugonga farm_users_pkey -
     * mtu aliyetolewa shambani hakuweza kurudishwa kamwe (409 ya "vikwazo vya
     * database"). Imethibitishwa na MultiFarmMembershipTest.
     *
     * Native kwa makusudi: JPQL ingechujwa na @SQLRestriction ile ile na
     * kutoiona safu inayotafutwa. created_at inabaki - ni historia ya lini
     * alianza mara ya kwanza; updated_* inasema nani alimrudisha.
     *
     * Inarudisha idadi ya safu: 0 = hakukuwa na uanachama uliotolewa.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update farm_users
               set is_deleted = false, deleted_at = null, deleted_by = null,
                   role_id = :roleId, updated_at = now(), updated_by = :byUserId
             where user_id = :userId and farm_id = :farmId and is_deleted = true
            """, nativeQuery = true)
    int restoreRemoved(@Param("userId") UUID userId, @Param("farmId") Integer farmId,
                       @Param("roleId") Integer roleId, @Param("byUserId") UUID byUserId);

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
