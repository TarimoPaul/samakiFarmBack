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

import java.util.List;
import java.util.Set;

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
            role.setPermissions(Set.copyOf(permissionRepository.findAllById(req.permissionIds())));
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
     */
    @Transactional
    public RoleSummary updateRolePermissions(Integer roleId, List<Integer> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role haipo"));

        role.setPermissions(Set.copyOf(permissionRepository.findAllById(permissionIds)));
        Role saved = roleRepository.save(role);

        JwtAuthFilter.clearRootCache();
        JwtAuthFilter.clearAllUserCache();

        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public Page<Permission> listAllPermissions(Pageable pageable) {
        return permissionRepository.findAll(pageable);
    }

    private static RoleSummary toSummary(Role role) {
        return new RoleSummary(role.getRoleId(), role.getName(), role.getDescription(),
                role.getPermissions() == null ? List.of() :
                        role.getPermissions().stream().map(Permission::getCode).toList());
    }
}
