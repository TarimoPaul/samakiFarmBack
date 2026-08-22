package com.samaki.farm.user.controller;

import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.farmuser.services.FarmUserService;
import com.samaki.farm.user.dto.AssignMembershipRequest;
import com.samaki.farm.user.dto.CreateUserRequest;
import com.samaki.farm.user.dto.UserSummary;
import com.samaki.farm.user.services.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST - User Management.
 *
 * Ruhusa mbili tofauti kwa makusudi:
 *   approve_users - kuidhinisha waliojisajili
 *   manage_users  - kuunda/kupanga/kuzuia/kufuta
 *
 * Hivyo mtu anaweza kupewa uwezo wa kuidhinisha bila kupewa uwezo wa
 * kubadilisha kila kitu.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FarmUserService farmUserService;

    public UserController(UserService userService, FarmUserService farmUserService) {
        this.userService = userService;
        this.farmUserService = farmUserService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<UserSummary> createUser(@Valid @RequestBody CreateUserRequest req) {
        return ApiResponse.ok(userService.createUser(req));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<List<UserSummary>> listUsers(@RequestParam Integer farmId) {
        return ApiResponse.ok(userService.listByFarm(farmId));
    }

    // ---------- Idhini (B4) ----------

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('approve_users')")
    public ApiResponse<List<UserSummary>> listPending() {
        return ApiResponse.ok(userService.listPending());
    }

    /** PENDING_APPROVAL -> ACTIVE. HAITOI role - hiyo ni hatua tofauti. */
    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasAuthority('approve_users')")
    public ApiResponse<UserSummary> approve(@PathVariable UUID userId) {
        return ApiResponse.ok(userService.approve(userId), "Mtumiaji ameidhinishwa.");
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<UserSummary> disable(@PathVariable UUID userId) {
        return ApiResponse.ok(userService.disable(userId), "Akaunti imezuiwa.");
    }

    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<UserSummary> enable(@PathVariable UUID userId) {
        return ApiResponse.ok(userService.enable(userId), "Akaunti imerudishwa.");
    }

    // ---------- Uanachama (B4) ----------

    @PostMapping("/{userId}/memberships")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<Void> assignMembership(@PathVariable UUID userId,
                                               @Valid @RequestBody AssignMembershipRequest req) {
        farmUserService.assignMembership(userId, req.farmId(), req.roleId());
        return ApiResponse.ok(null, "Mtumiaji amewekwa kwenye shamba.");
    }

    @PutMapping("/{userId}/memberships/{farmId}/role")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<Void> changeRole(@PathVariable UUID userId, @PathVariable Integer farmId,
                                         @Valid @RequestBody AssignMembershipRequest req) {
        farmUserService.changeRole(userId, farmId, req.roleId());
        return ApiResponse.ok(null, "Role imebadilishwa.");
    }

    @DeleteMapping("/{userId}/memberships/{farmId}")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<Void> removeMembership(@PathVariable UUID userId, @PathVariable Integer farmId) {
        farmUserService.removeMembership(userId, farmId);
        return ApiResponse.ok(null, "Mtumiaji ametolewa kwenye shamba.");
    }

    // ---------- Kufuta mtu ----------

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('manage_users')")
    public ApiResponse<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ApiResponse.ok(null, "Mtumiaji amefutwa.");
    }
}
