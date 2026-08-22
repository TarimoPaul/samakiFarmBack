package com.samaki.farm.rbac.controller;

import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.common.web.ApiResponsePage;
import com.samaki.farm.common.web.PageableParam;
import com.samaki.farm.rbac.dto.CreateRoleRequest;
import com.samaki.farm.rbac.dto.RoleSummary;
import com.samaki.farm.rbac.entity.Permission;
import com.samaki.farm.rbac.services.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST - Role & Permission management (FR: manage_users).
 *
 * Ukaguzi wa ruhusa ni kupitia @PreAuthorize (kama Lsms RoleController), si
 * kwa mkono - inahitaji MethodSecurityConfig + JwtAuthFilter kuweka
 * authorities fresh kutoka DB kwenye Authentication.
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<List<RoleSummary>> listRoles() {
        return ApiResponse.ok(roleService.listRoles());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<RoleSummary> createRole(@RequestBody CreateRoleRequest req) {
        return ApiResponse.ok(roleService.createRole(req));
    }

    /** Badilisha (replace kabisa) ruhusa za role fulani. */
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<RoleSummary> updateRolePermissions(@PathVariable Integer roleId,
                                                           @RequestBody List<Integer> permissionIds) {
        return ApiResponse.ok(roleService.updateRolePermissions(roleId, permissionIds));
    }

    /**
     * Paginated (kama Lsms) - endpoint hii ndiyo iliyochaguliwa kwa mfano kwa
     * sababu orodha ya permissions inakua kila feature mpya (tofauti na
     * roles ambazo ni chache tu).
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponsePage<Permission> listAllPermissions(PageableParam pageParam) {
        return ApiResponsePage.of(roleService.listAllPermissions(pageParam.toPageable("code")));
    }
}
