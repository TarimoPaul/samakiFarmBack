package com.samaki.farm.rbac.services;

import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.dto.CreateRoleRequest;
import com.samaki.farm.rbac.dto.RoleSummary;
import com.samaki.farm.rbac.dto.UpdateRoleRequest;
import com.samaki.farm.rbac.entity.Permission;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.rbac.repository.PermissionRepository;
import com.samaki.farm.rbac.repository.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mtiririko: createRole (owner anaunda role mpya) -> updateRolePermissions
 * (anaweka ruhusa zake) -> mtumiaji anapewa role wakati anapoundwa
 * (FarmUser.role, angalia FarmUserService).
 *
 * MWISHO WA MAISHA YA NAFASI, njia MBILI zenye maana tofauti kabisa:
 *
 *   setActive(id, false)  KUZIMA - inarudishwa. Nafasi inabaki kwenye
 *                         orodha, walioshikilia hawaguswi hata kidogo,
 *                         na kinachozuiliwa ni kuipa mtu MPYA. Hii ndiyo
 *                         njia ya "hatuitumii tena" isiyogusa mtu yeyote.
 *   deleteRole(id)        KUFUTA - soft-delete, inatoweka. Inakataliwa
 *                         kabisa endapo mtu yeyote bado anaishikilia,
 *                         kwa sababu kuiondoa kungemnyang'anya ruhusa
 *                         zake bila yeye wala msimamizi kuona ilipotokea.
 *
 * Kuwa na moja tu kungelazimisha uchaguzi mbaya: soft-delete pekee
 * ingemaanisha kuzima = kuficha (na kufichwa kwenye skrini pekee
 * inayoweza kurudisha), na kufuta pekee kungemaanisha kila
 * "hatuitumii tena" ni tukio la kupoteza data.
 */
@Service
public class RoleService {

    /** `roles.name` ni VARCHAR(50) - angalia V1__init_schema.sql. */
    private static final int NAME_MAX_LENGTH = 50;

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final FarmUserRepository farmUserRepository;
    private final PermissionChecker permissionChecker;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                       FarmUserRepository farmUserRepository, PermissionChecker permissionChecker) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.farmUserRepository = farmUserRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * Nafasi ZOTE zilizopo, ikiwemo zilizozimwa.
     *
     * Zilizozimwa ZINAJUMUISHWA kwa makusudi: skrini ya wasimamizi ndiyo
     * pekee inayoweza kuzirudisha, hivyo kuzificha hapa kungezifanya
     * zisifikike kabisa. `active` kwenye RoleSummary ndiyo inayowaruhusu
     * wateja wengine (kichagua-nafasi cha Wanachama) kuchuja wenyewe.
     */
    @Transactional(readOnly = true)
    public List<RoleSummary> listRoles() {
        return roleRepository.findAll().stream().map(RoleService::toSummary).toList();
    }

    @Transactional
    public RoleSummary createRole(CreateRoleRequest req) {
        Role role = new Role();
        role.setName(requireAvailableName(req.name(), null));
        role.setDescription(normaliseDescription(req.description()));
        if (req.permissionIds() != null) {
            role.setPermissions(resolvePermissions(req.permissionIds()));
        }
        Role saved = roleRepository.save(role);

        // Role mpya inaathiri authorities za ROOT (inaongeza jina jipya la role
        // kwenye orodha yake) - futa cache kama Lsms saveRole().
        JwtAuthFilter.clearRootCache();

        return toSummary(saved);
    }

    /**
     * Jina na maelezo. HAIGUSI ruhusa wala hali ya `active`.
     *
     * Kubadilisha jina hakuathiri mtu yeyote anayeishikilia: uanachama
     * unaonyesha kwa role_id, si kwa jina. Kinachobadilika ni orodha ya
     * authorities za ROOT (ina majina ya role zote zenye kiambishi
     * ROLE_), hivyo cache yake pekee ndiyo inafutwa.
     */
    @Transactional
    public RoleSummary updateRole(Integer roleId, UpdateRoleRequest req) {
        Role role = requireRole(roleId);

        role.setName(requireAvailableName(req.name(), roleId));
        role.setDescription(normaliseDescription(req.description()));
        Role saved = roleRepository.save(role);

        JwtAuthFilter.clearRootCache();

        return toSummary(saved);
    }

    /**
     * Kuzima au kurudisha nafasi.
     *
     * HAKUNA cache inayofutwa hapa, na si usahaulifu: kuzima
     * HAKUBADILISHI ruhusa za mtu yeyote. Walioshikilia nafasi hii
     * wanaendelea nayo na kila kitu wanachoweza kufanya kinabaki vile
     * vile - kinachozuiliwa ni kuipa mtu MPYA (FarmUserService.resolveRole).
     * Kufuta cache kungewalazimisha watumiaji wote kusomwa upya DB-ni
     * bila kitu chochote kubadilika kwao.
     *
     * Idempotent: kuzima iliyokwisha zimwa ni sawa, inarudisha hali ilivyo.
     */
    @Transactional
    public RoleSummary setActive(Integer roleId, boolean active) {
        Role role = requireRole(roleId);
        role.setActive(active);
        return toSummary(roleRepository.save(role));
    }

    /**
     * Soft-delete ya nafasi - INAKATALIWA ikiwa bado inashikiliwa.
     *
     * Ukaguzi wa wanaoishikilia ndio moyo wa method hii. `farm_users.role_id`
     * HAINA `ON DELETE CASCADE` (V1), na hata kama ingekuwa nayo, hii ni
     * soft-delete: safu za uanachama zingebaki zikielekeza kwenye nafasi
     * ambayo kila query inaificha. Matokeo yake mtu angepoteza ruhusa zake
     * ZOTE kimyakimya, na dalili pekee ingekuwa ni yeye kushindwa kufanya
     * kazi aliyokuwa akiifanya jana - bila skrini yoyote kuonyesha kwa nini.
     *
     * Kwa hiyo kikwazo ni cha wazi na kinachoweza kupitwa: badilisha
     * nafasi za watu hao, kisha ombi lilelile linafanikiwa. Msimamizi
     * asiyetaka kufanya hivyo ana njia ya pili - kuizima.
     *
     * Cache ya ROOT inafutwa (jina la role limetoka kwenye orodha yake);
     * cache za watumiaji HAZIFUTWI kwa sababu, baada ya ukaguzi hapo juu,
     * hakuna mtumiaji hata mmoja aliyeguswa.
     */
    @Transactional
    public void deleteRole(Integer roleId) {
        Role role = requireRole(roleId);

        long holders = farmUserRepository.countByRole_RoleId(roleId);
        if (holders > 0) {
            throw new ConflictException(
                    "Nafasi hii inashikiliwa na watu " + holders
                            + ". Wabadilishie nafasi nyingine kwanza, au izime badala ya kuifuta.",
                    ErrorCodes.ROLE_IN_USE);
        }

        role.softDelete(permissionChecker.currentUser().getUserId());
        roleRepository.save(role);

        JwtAuthFilter.clearRootCache();
    }

    /**
     * Badilisha (replace kabisa) ruhusa za role fulani - kama Lsms
     * assignOrRemovePermissionsToRole(). Cache za watumiaji WOTE zinafutwa
     * kwa sababu hatuwezi kujua papo hapo ni watumiaji gani wanashikilia
     * role hii.
     *
     * NI YOTE-AU-HAKUNA: resolvePermissions() inathibitisha vitambulisho
     * VYOTE kabla ya kugusa role. Kitambulisho kimoja tu kisichokuwepo
     * kinakata ombi zima kabla ya setPermissions(), hivyo role inabaki
     * kama ilivyokuwa - na cache HAZIFUTWI, kwa sababu hakuna
     * kilichobadilika cha kuzifanya kuwa za zamani.
     */
    @Transactional
    public RoleSummary updateRolePermissions(Integer roleId, List<Integer> permissionIds) {
        Role role = requireRole(roleId);

        Set<Permission> permissions = resolvePermissions(permissionIds);

        role.setPermissions(permissions);
        Role saved = roleRepository.save(role);

        JwtAuthFilter.clearRootCache();
        JwtAuthFilter.clearAllUserCache();

        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public Page<Permission> listAllPermissions(Pageable pageable) {
        return permissionRepository.findAll(pageable);
    }

    /**
     * Nafasi hii, ikiwa BADO IPO.
     *
     * `findById()` peke yake HAITOSHI hapa. Hibernate haitumii
     * @SQLRestriction kwenye lookup ya moja kwa moja ya PK (jambo
     * lililoandikwa wazi kwenye BaseEntity), hivyo inarudisha hata nafasi
     * iliyofutwa. Bila ukaguzi huu, DELETE ingeweza kuitwa mara mbili
     * ikiripoti mafanikio mara zote mbili, na PUT ingeweza kuifufua nafasi
     * iliyofutwa kimyakimya kwa kuihariri.
     */
    private Role requireRole(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role haipo"));
        if (role.isDeleted()) {
            throw new IllegalArgumentException("Role haipo");
        }
        return role;
    }

    /**
     * Jina lililopunguzwa nafasi tupu, likiwa halali na halijachukuliwa.
     *
     * Ukaguzi upo hapa - si kwenye bean validation - kwa sababu unahitaji
     * database: `roles.name` ni UNIQUE, na jibu la "limechukuliwa?"
     * linategemea nafasi zilizopo, ikiwemo zilizofutwa (angalia
     * RoleRepository.countByNameIncludingDeleted).
     *
     * Awali hapakuwa na ukaguzi wowote: jina tupu au lililojirudia
     * lilifika database na kurudi kama DataIntegrityViolationException,
     * yaani 409 yenye sentensi moja ya jumla inayotumika kwa kila kikwazo
     * cha database. Msimamizi aliambiwa "operesheni imekiuka vikwazo vya
     * database" bila kuelezwa kuwa tatizo ni jina tu.
     */
    private String requireAvailableName(String raw, Integer selfId) {
        String name = raw == null ? "" : raw.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la nafasi linahitajika.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Jina la nafasi lisizidi herufi " + NAME_MAX_LENGTH + ".");
        }
        if (roleRepository.countByNameIncludingDeleted(name, selfId) > 0) {
            throw new ConflictException("Nafasi yenye jina hili tayari ipo.");
        }
        return name;
    }

    /**
     * Maelezo matupu ni KUTOKUWA na maelezo, si maelezo yasiyo na kitu:
     * `roles.description` inaruhusiwa kuwa null, na "" ingeonekana kwenye
     * UI kama maelezo yaliyopo lakini yasiyosema chochote.
     */
    private static String normaliseDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Geuza vitambulisho vya ruhusa kuwa entities - au KATAA ombi zima.
     *
     * Sheria: kitambulisho chochote kisichokuwepo (au null ndani ya
     * orodha) kinakata ombi lote kwa IllegalArgumentException, ambayo
     * GlobalExceptionHandler inaigeuza 400 + errorCode VALIDATION_ERROR
     * (na GraphQlExceptionResolver inatuma msimbo uleule).
     *
     * KWA NINI kukataa badala ya kukubali sehemu: awali hapa palikuwa na
     * findAllById() peke yake, ambayo inatupa kimya vitambulisho
     * visivyokuwepo. Ombi la kuweka ruhusa 5 likiwa na kitambulisho kimoja
     * kibaya lilirudisha 200 likiwa limeweka 4 - mteja akaambiwa
     * "imefanikiwa" ilhali role ina ruhusa PUNGUFU kuliko alizoomba, na
     * hii ni endpoint ya kuandika sera ya usalama. Kukosa ruhusa kimya ni
     * hitilafu inayoonekana baadaye tu, mahali pengine kabisa (mtumiaji
     * anazuiwa kufanya kitu anachopaswa kuruhusiwa).
     *
     * Uthibitisho WOTE unafanyika kabla ya kuandika lolote: role
     * haiguswi hadi orodha nzima ijulikane kuwa sahihi.
     *
     * Rudufu si kosa - kuomba ruhusa ileile mara mbili ni ombi lilelile;
     * inaondolewa kimya (LinkedHashSet).
     *
     * LAZIMA irudishe Set inayobadilika. Set.copyOf(...) inarudisha
     * immutable set, na wakati wa merge Hibernate huita clear() kwenye
     * collection ya entity - hivyo kuhifadhi role yenye ruhusa mpya
     * kulikuwa kunatupa UnsupportedOperationException, yaani 500 kwa kila
     * PUT /api/roles/{id}/permissions (D-13).
     */
    private Set<Permission> resolvePermissions(List<Integer> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            // Orodha tupu ni halali kimakusudi: ndiyo njia ya kuondoa ruhusa
            // ZOTE za role.
            return new LinkedHashSet<>();
        }

        // Mpangilio wa mteja unahifadhiwa (LinkedHashSet) ili ujumbe wa
        // hitilafu utaje vitambulisho kwa mpangilio ule ule alioutuma.
        Set<Integer> wanted = new LinkedHashSet<>(permissionIds);
        if (wanted.contains(null)) {
            throw new IllegalArgumentException(
                    "Orodha ya ruhusa ina thamani tupu (null). Hakuna kilichobadilishwa.");
        }

        Map<Integer, Permission> found = permissionRepository.findAllById(wanted).stream()
                .collect(Collectors.toMap(Permission::getPermissionId, p -> p,
                        (a, b) -> a, LinkedHashMap::new));

        if (found.size() != wanted.size()) {
            String unknown = wanted.stream()
                    .filter(id -> !found.containsKey(id))
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Ruhusa hizi hazipo: " + unknown + ". Hakuna kilichobadilishwa.");
        }

        // Mpangilio wa mteja tena, si ule wa findAllById.
        return wanted.stream()
                .map(found::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static RoleSummary toSummary(Role role) {
        return new RoleSummary(role.getRoleId(), role.getName(), role.getDescription(),
                role.isActive(),
                role.getPermissions() == null ? List.of() :
                        role.getPermissions().stream().map(Permission::getCode).toList());
    }
}
