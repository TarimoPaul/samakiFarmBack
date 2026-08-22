package com.samaki.farm.farmuser.controller;

import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.farmuser.dto.CreateUserRequest;
import com.samaki.farm.farmuser.dto.UpdateUserRoleRequest;
import com.samaki.farm.farmuser.dto.UserSummary;
import com.samaki.farm.farmuser.services.FarmUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST - User Management. @PreAuthorize inakagua ruhusa TULI (manage_users);
 * ukaguzi wa muktadha (shamba la mtumiaji, ROOT, self-delete) uko ndani ya
 * FarmUserService kwa sababu unategemea thamani za request.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final FarmUserService farmUserService;

    public UserController(FarmUserService farmUserService) {
        this.farmUserService = farmUserService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<UserSummary> createUser(@RequestBody CreateUserRequest req) {
        return ApiResponse.ok(farmUserService.createUser(req));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<List<UserSummary>> listUsers(@RequestParam Integer farmId) {
        return ApiResponse.ok(farmUserService.listUsers(farmId));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<UserSummary> updateUserRole(@PathVariable UUID userId,
                                                    @Valid @RequestBody UpdateUserRoleRequest req) {
        return ApiResponse.ok(farmUserService.updateRole(userId, req.roleId()));
    }

    /** Soft-delete - rekodi inabaki DB kwa historia, angalia FarmUserService.deleteUser. */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<Void> deleteUser(@PathVariable UUID userId) {
        farmUserService.deleteUser(userId);
        return ApiResponse.ok(null, "Mtumiaji amefutwa.");
    }
}
