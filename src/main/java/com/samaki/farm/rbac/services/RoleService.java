package com.samaki.farm.rbac.services;

import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.rbac.dto.CreateRoleRequest;
import com.samaki.farm.rbac.dto.RoleSummary;
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
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> listRoles() {
        return roleRepository.findAll().stream()
                .map(r -> new RoleSummary(r.getRoleId(), r.getName(), r.getDescription(),
                        r.getPermissions() == null ? List.of() :
                                r.getPermissions().stream().map(Permission::getCode).toList()))
                .toList();
    }

    @Transactional
    public RoleSummary createRole(CreateRoleRequest req) {
        Role role = new Role();
        role.setName(req.name());
        role.setDescription(req.description());
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
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role haipo"));

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
                role.getPermissions() == null ? List.of() :
                        role.getPermissions().stream().map(Permission::getCode).toList());
    }
}
