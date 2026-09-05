package com.samaki.farm.user.services;

import com.samaki.farm.auth.security.AuthenticatedUser;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.user.dto.CreateUserRequest;
import com.samaki.farm.user.dto.UpdateUserRequest;
import com.samaki.farm.user.dto.UserSummary;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import com.samaki.farm.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Mzunguko wa maisha wa mtu: kuunda, kuidhinisha, kuzuia, kufuta.
 * Uanachama (shamba + role) ni FarmUserService - ni dhana tofauti.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final FarmUserRepository farmUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionChecker permissionChecker;

    public UserService(UserRepository userRepository, FarmUserRepository farmUserRepository,
                        PasswordEncoder passwordEncoder, PermissionChecker permissionChecker) {
        this.userRepository = userRepository;
        this.farmUserRepository = farmUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionChecker = permissionChecker;
    }

    /** Msimamizi anaunda mtu - anaanza ACTIVE (msimamizi ndiye idhini). */
    @Transactional
    public UserSummary createUser(CreateUserRequest req) {
        String phone = requireAvailablePhone(req.phone(), null);
        String email = requireAvailableEmail(req.email(), null);

        User user = new User();
        user.setName(req.name().trim());
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(UserStatus.ACTIVE);

        return toSummary(userRepository.save(user), null);
    }

    /**
     * Utambulisho wa mtu: jina, simu, barua pepe. Hakuna kingine.
     *
     * HAIGUSI password, hali ya akaunti wala uanachama - kila kimoja kina
     * endpoint yake. Ni sheria ile ile ya RoleService.updateRole: ombi la
     * kurekebisha jina halipaswi kuwa na uwezo wa kubadilisha kile mtu
     * anachoweza kufanya.
     *
     * INARUHUSIWA kwa mtu MWENYEWE, tofauti na disable/delete. Kubadilisha
     * jina lako au namba yako hakukufungi nje ya mfumo - unaendelea kuingia,
     * kwa namba mpya. Kikwazo cha requireNotSelf kingekuwa cha kubuni.
     *
     * HAKUNA cache inayofutwa: JwtAuthFilter inahifadhi userId, shamba, role
     * na ruhusa - hakuna jina wala namba ndani yake, hivyo hakuna
     * kilichopitwa na wakati. (Kuingia ni kwa namba, lakini hilo ni swali
     * jipya kila mara, si cache.)
     */
    @Transactional
    public UserSummary updateUser(UUID userId, UpdateUserRequest req) {
        User user = requireManageableUser(userId);

        user.setName(req.name().trim());
        user.setPhone(requireAvailablePhone(req.phone(), userId));
        user.setEmail(requireAvailableEmail(req.email(), userId));

        return toSummary(userRepository.save(user), null);
    }

    /**
     * Namba iliyopunguzwa nafasi tupu, ikiwa haijachukuliwa na mtu mwingine.
     *
     * Ukaguzi unahesabu hata waliofutwa (angalia
     * UserRepository.countByPhoneIncludingDeleted): `users.phone` ni UNIQUE
     * kwenye jedwali lote, si kwa wasiofutwa tu. Awali `existsByPhone`
     * peke yake ilitumika hapa, hivyo kutumia tena namba ya mtu aliyefutwa
     * kulipita ukaguzi na kurudi kama hitilafu ya jumla ya database.
     */
    private String requireAvailablePhone(String raw, UUID selfId) {
        String phone = raw == null ? "" : raw.trim();
        if (phone.isEmpty()) {
            throw new IllegalArgumentException("Namba ya simu inahitajika.");
        }
        if (userRepository.countByPhoneIncludingDeleted(phone, selfId) > 0) {
            throw new ConflictException("Namba ya simu hii tayari imesajiliwa.");
        }
        return phone;
    }

    /**
     * Barua pepe, au NULL.
     *
     * Tupu inakuwa null, si "": safu ni UNIQUE na inaruhusu NULL nyingi,
     * lakini "" moja tu - hivyo mtu wa pili asiye na barua pepe angekataliwa
     * kwa sababu isiyoeleweka kabisa.
     */
    private String requireAvailableEmail(String raw, UUID selfId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String email = raw.trim();
        if (userRepository.countByEmailIncludingDeleted(email, selfId) > 0) {
            throw new ConflictException("Barua pepe hii tayari imesajiliwa.");
        }
        return email;
    }

    /** Wanaosubiri idhini - ndio orodha ambayo mwenye approve_users anaifanyia kazi. */
    @Transactional(readOnly = true)
    public List<UserSummary> listPending() {
        return userRepository.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING_APPROVAL).stream()
                .map(u -> toSummary(u, null))
                .toList();
    }

    /**
     * B4 - PENDING_APPROVAL -> ACTIVE. Idhini PEKEE: haitoi shamba wala
     * role. Kupewa role ni hatua tofauti (FarmUserService.assignMembership).
     */
    @Transactional
    public UserSummary approve(UUID userId) {
        User user = requireManageableUser(userId);
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new ConflictException("Mtumiaji huyu hayuko kwenye hali ya kusubiri idhini.");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        JwtAuthFilter.clearUserCache(userId);

        return toSummary(user, null);
    }

    /** Kuzuia akaunti. Rekodi inabaki (tofauti na kufuta) na inaweza kurudishwa. */
    @Transactional
    public UserSummary disable(UUID userId) {
        User user = requireManageableUser(userId);
        requireNotSelf(userId, "Huwezi kujizuia mwenyewe.");

        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        // Muhimu: bila hii, token yake ingeendelea kufanya kazi hadi cache
        // ya dakika 15 iishe muda.
        JwtAuthFilter.clearUserCache(userId);

        return toSummary(user, null);
    }

    /** Kurudisha akaunti iliyozuiwa. */
    @Transactional
    public UserSummary enable(UUID userId) {
        User user = requireManageableUser(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        JwtAuthFilter.clearUserCache(userId);
        return toSummary(user, null);
    }

    /**
     * Soft-delete. Rekodi inabaki DB kwa ajili ya historia (feeding_logs/
     * task_completions zinamrejelea), lakini haionekani tena kwenye query
     * zozote za kawaida.
     */
    @Transactional
    public void deleteUser(UUID userId) {
        AuthenticatedUser caller = permissionChecker.currentUser();
        requireNotSelf(userId, "Huwezi kujifuta mwenyewe.");

        User user = requireManageableUser(userId);

        // Shamba lisibaki bila mmiliki - Farm.owner ingeelekea rekodi
        // iliyofutwa, na hakuna njia ya kuweka mmiliki mwingine bado.
        boolean ownsAFarm = farmUserRepository.findByUser_UserIdOrderByFarm_FarmIdAsc(userId).stream()
                .anyMatch(m -> m.getFarm().getOwner() != null
                        && userId.equals(m.getFarm().getOwner().getUserId()));
        if (ownsAFarm) {
            throw new ConflictException("Mmiliki wa shamba hawezi kufutwa.");
        }

        user.softDelete(caller.getUserId());
        userRepository.save(user);
        JwtAuthFilter.clearUserCache(userId);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listByFarm(Integer farmId) {
        permissionChecker.requireSameFarm(farmId);
        return farmUserRepository.findByFarm_FarmIdOrderByUser_NameAsc(farmId).stream()
                .map(m -> toSummary(m.getUser(), m))
                .toList();
    }

    private User requireManageableUser(UUID userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Mtumiaji hayupo"));
        if (Boolean.TRUE.equals(user.getIsRoot())) {
            throw new AccessDeniedException("Mtumiaji wa ROOT habadilishwi kupitia API hii.");
        }
        return user;
    }

    private void requireNotSelf(UUID userId, String message) {
        if (userId.equals(permissionChecker.currentUser().getUserId())) {
            throw new IllegalArgumentException(message);
        }
    }

    /** membership inaweza kuwa null - mtu anaweza kuwa hana uanachama wowote. */
    static UserSummary toSummary(User user, FarmUser membership) {
        Integer farmId = null;
        String roleName = null;
        if (membership != null) {
            farmId = membership.getFarm() == null ? null : membership.getFarm().getFarmId();
            roleName = membership.getRole() == null ? null : membership.getRole().getName();
        }
        return new UserSummary(user.getUserId().toString(), user.getName(), user.getPhone(),
                user.getStatus().name(), farmId, roleName);
    }
}
