package com.samaki.farm.farmuser.services;

import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.rbac.repository.RoleRepository;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UANACHAMA - kumweka mtu kwenye shamba na kumpa role hapo.
 *
 * Ni dhana TOFAUTI na idhini (UserService.approve): mtu anaweza
 * kuidhinishwa bila uanachama, au kupewa uanachama kabla hajaidhinishwa.
 * Vyote viwili ni halali (Part A #4).
 */
@Service
public class FarmUserService {

    private final FarmUserRepository farmUserRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final RoleRepository roleRepository;
    private final PermissionChecker permissionChecker;

    public FarmUserService(FarmUserRepository farmUserRepository, UserRepository userRepository,
                            FarmRepository farmRepository, RoleRepository roleRepository,
                            PermissionChecker permissionChecker) {
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
        this.farmRepository = farmRepository;
        this.roleRepository = roleRepository;
        this.permissionChecker = permissionChecker;
    }

    /** Kumweka mtu kwenye shamba. roleId inaruhusiwa kuwa null. */
    @Transactional
    public void assignMembership(UUID userId, Integer farmId, Integer roleId) {
        permissionChecker.requireSameFarm(farmId);

        User user = requireNonRootUser(userId);
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new IllegalArgumentException("Farm haipo"));

        if (farmUserRepository.existsByUser_UserIdAndFarm_FarmId(userId, farmId)) {
            throw new ConflictException("Mtumiaji huyu tayari yupo kwenye shamba hili.");
        }

        FarmUser membership = new FarmUser();
        membership.setUser(user);
        membership.setFarm(farm);
        membership.setRole(resolveRole(roleId));
        farmUserRepository.save(membership);

        // Ruhusa zake zimebadilika - futa cache ili zianze kufanya kazi papo hapo.
        JwtAuthFilter.clearUserCache(userId);
    }

    /** Kubadilisha role ya mtu kwenye shamba fulani. */
    @Transactional
    public void changeRole(UUID userId, Integer farmId, Integer roleId) {
        permissionChecker.requireSameFarm(farmId);
        requireNonRootUser(userId);

        FarmUser membership = farmUserRepository.findByUser_UserIdAndFarm_FarmId(userId, farmId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mtumiaji huyu hayupo kwenye shamba hili."));

        membership.setRole(resolveRole(roleId));
        farmUserRepository.save(membership);
        JwtAuthFilter.clearUserCache(userId);
    }

    /** Kumtoa mtu kwenye shamba (soft-delete ya uanachama, si ya mtu). */
    @Transactional
    public void removeMembership(UUID userId, Integer farmId) {
        permissionChecker.requireSameFarm(farmId);

        FarmUser membership = farmUserRepository.findByUser_UserIdAndFarm_FarmId(userId, farmId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mtumiaji huyu hayupo kwenye shamba hili."));

        Farm farm = membership.getFarm();
        if (farm.getOwner() != null && userId.equals(farm.getOwner().getUserId())) {
            // Msimbo mahususi: hiki si kigongano kinachoweza kurekebishwa na
            // kujaribiwa tena (kama rudufu), ni sheria isiyobadilika kwa mtu
            // huyu kwenye shamba hili. Ujumbe ni ule ule uliokuwepo.
            throw new ConflictException("Mmiliki wa shamba hawezi kutolewa kwenye shamba lake.",
                    ErrorCodes.OWNER_IMMUTABLE);
        }

        membership.softDelete(permissionChecker.currentUser().getUserId());
        farmUserRepository.save(membership);
        JwtAuthFilter.clearUserCache(userId);
    }

    private User requireNonRootUser(UUID userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Mtumiaji hayupo"));
        if (Boolean.TRUE.equals(user.getIsRoot())) {
            // ROOT hapati uanachama: ufikiaji wake unatoka kwenye flag, si uhusiano.
            throw new AccessDeniedException("Mtumiaji wa ROOT hapewi uanachama wa shamba.");
        }
        return user;
    }

    private Role resolveRole(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role haipo"));
    }
}
