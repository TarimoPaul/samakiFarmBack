package com.samaki.farm.farmuser.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.dto.CreateUserRequest;
import com.samaki.farm.farmuser.dto.UserSummary;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.rbac.repository.RoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FarmUserService {

    private final FarmUserRepository farmUserRepository;
    private final FarmRepository farmRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionChecker permissionChecker;

    public FarmUserService(FarmUserRepository farmUserRepository, FarmRepository farmRepository,
                            RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                            PermissionChecker permissionChecker) {
        this.farmUserRepository = farmUserRepository;
        this.farmRepository = farmRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionChecker = permissionChecker;
    }

    /**
     * FR: "user akishatengenezwa ndio anakuwa assigned role/permission" -
     * baada ya kuunganisha User+FarmUser kuwa entity moja, mtumiaji anazaliwa
     * akiwa tayari na farm + role, kwa save MOJA.
     */
    @Transactional
    public UserSummary createUser(CreateUserRequest req) {
        // Ukaguzi wa MUKTADHA (thamani ya request), si ruhusa tuli - hivyo uko
        // hapa badala ya @PreAuthorize ya controller.
        permissionChecker.requireSameFarm(req.farmId());

        // Rudufu inakaguliwa hapa ili mteja apate ujumbe wa Kiswahili
        // unaoeleweka (409) badala ya DataIntegrityViolationException ya
        // jumla kutoka kwenye UNIQUE constraint ya database.
        if (farmUserRepository.existsByPhone(req.phone())) {
            throw new ConflictException("Namba ya simu hii tayari imesajiliwa.");
        }
        if (req.email() != null && !req.email().isBlank() && farmUserRepository.existsByEmail(req.email())) {
            throw new ConflictException("Barua pepe hii tayari imesajiliwa.");
        }

        // Farm/Role zinathibitishwa KABLA ya kuunda mtumiaji - awali mtumiaji
        // alihifadhiwa kwanza, hivyo farmId/roleId isiyokuwepo iliacha rekodi
        // ya mtu asiye na shamba (method ile haikuwa @Transactional).
        Farm farm = farmRepository.findById(req.farmId())
                .orElseThrow(() -> new IllegalArgumentException("Farm haipo"));
        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new IllegalArgumentException("Role haipo"));

        FarmUser user = new FarmUser();
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFarm(farm);
        user.setRole(role);

        return toSummary(farmUserRepository.save(user));
    }

    /**
     * Watumiaji wa shamba - query iliyowezekana tu baada ya kuunganisha
     * entity mbili. Waliofutwa (soft-delete) hawaonekani: @SQLRestriction ya
     * FarmUser inachuja derived query hii.
     */
    @Transactional(readOnly = true)
    public List<UserSummary> listUsers(Integer farmId) {
        permissionChecker.requireSameFarm(farmId);
        return farmUserRepository.findByFarm_FarmId(farmId).stream()
                .map(FarmUserService::toSummary)
                .toList();
    }

    /**
     * Kubadilisha role ya mtumiaji aliyepo.
     *
     * clearUserCache ni MUHIMU: JwtAuthFilter inashikilia ruhusa za kila
     * mtumiaji kwa dakika 15, hivyo bila kufuta cache mtumiaji angeendelea
     * kutumia ruhusa za role ya zamani hadi cache iishe muda.
     */
    @Transactional
    public UserSummary updateRole(UUID userId, Integer roleId) {
        FarmUser user = requireManageableUser(userId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role haipo"));

        user.setRole(role);
        farmUserRepository.save(user);
        JwtAuthFilter.clearUserCache(userId);

        return toSummary(user);
    }

    /**
     * Soft-delete ya mtumiaji - rekodi inabaki database kwa ajili ya historia
     * (feeding_logs/task_completions zinamrejelea), lakini haionekani tena
     * kwenye query zozote za kawaida.
     *
     * Athari ya usalama: findByUserId ya JwtAuthFilter ni derived query,
     * hivyo baada ya hapa token yake haitampa ruhusa yoyote - hata kabla
     * haijaisha muda.
     */
    @Transactional
    public void deleteUser(UUID userId) {
        AuthenticatedUser caller = permissionChecker.currentUser();
        if (userId.equals(caller.getUserId())) {
            throw new IllegalArgumentException("Huwezi kujifuta mwenyewe.");
        }

        FarmUser user = requireManageableUser(userId);

        // Shamba lisibaki bila mmiliki - Farm.owner ingeelekea rekodi
        // iliyofutwa, na hakuna njia ya kuweka mmiliki mwingine bado.
        Farm farm = user.getFarm();
        if (farm.getOwner() != null && userId.equals(farm.getOwner().getUserId())) {
            throw new IllegalArgumentException("Mmiliki wa shamba hawezi kufutwa.");
        }

        user.softDelete(caller.getUserId());
        farmUserRepository.save(user);
        JwtAuthFilter.clearUserCache(userId);
    }

    /**
     * Ukaguzi wa pamoja kwa operesheni zinazobadilisha mtumiaji mwingine:
     * lazima awepo, asiwe ROOT, na awe kwenye shamba lile lile la anayeomba.
     */
    private FarmUser requireManageableUser(UUID userId) {
        FarmUser user = farmUserRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Mtumiaji hayupo"));

        if (Boolean.TRUE.equals(user.getIsRoot())) {
            throw new AccessDeniedException("Mtumiaji wa ROOT habadilishwi kupitia API hii.");
        }
        if (user.getFarm() == null) {
            throw new AccessDeniedException("Mtumiaji huyu hajaunganishwa na shamba lolote.");
        }
        permissionChecker.requireSameFarm(user.getFarm().getFarmId());

        return user;
    }

    private static UserSummary toSummary(FarmUser user) {
        return new UserSummary(user.getUserId().toString(), user.getName(),
                user.getRole() == null ? null : user.getRole().getName());
    }
}
