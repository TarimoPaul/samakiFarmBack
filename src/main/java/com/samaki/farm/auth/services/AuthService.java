package com.samaki.farm.auth.services;

import com.samaki.farm.auth.dto.ForgotPasswordRequest;
import com.samaki.farm.auth.dto.LoginRequest;
import com.samaki.farm.auth.dto.LoginResponse;
import com.samaki.farm.auth.dto.ResetPasswordRequest;
import com.samaki.farm.auth.dto.SignupRequest;
import com.samaki.farm.auth.security.JwtUtil;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.UnauthorizedException;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.dto.UserSummary;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.rbac.repository.RoleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logic yote ya signup/login/forgot-password. AuthController ni HTTP tu.
 *
 * Hitilafu zinatolewa kama exceptions (ConflictException,
 * UnauthorizedException, IllegalArgumentException, AccessDeniedException) -
 * si ResponseEntity zenye status - ili service hii isijue chochote kuhusu
 * HTTP. Ramani ya exception -> status code iko GlobalExceptionHandler.
 */
@Service
public class AuthService {

    private final FarmUserRepository farmUserRepository;
    private final FarmRepository farmRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;

    public AuthService(FarmUserRepository farmUserRepository, FarmRepository farmRepository,
                        RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil, PasswordResetService passwordResetService) {
        this.farmUserRepository = farmUserRepository;
        this.farmRepository = farmRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Shamba jipya + mmiliki wake. Role ni OWNER kila wakati (si kutoka
     * client), na mtumiaji anaingizwa moja kwa moja (auto-login).
     */
    @Transactional
    public LoginResponse signup(SignupRequest req) {
        if (farmUserRepository.existsByPhone(req.phone())) {
            throw new ConflictException("Namba ya simu hii tayari imesajiliwa.");
        }

        Role ownerRole = roleRepository.findByName("OWNER")
                .orElseThrow(() -> new IllegalStateException(
                        "Role ya OWNER haijawekwa kwenye mfumo - wasiliana na msimamizi."));

        // Farm na FarmUser zinategemeana pande zote mbili (farms.owner_user_id
        // -> farm_users, farm_users.farm_id -> farms), hivyo save ni hatua
        // tatu: mtumiaji kwanza (bila shamba) -> shamba lenye owner ->
        // mtumiaji anapewa shamba lake. Zote ndani ya transaction moja.
        FarmUser user = new FarmUser();
        user.setName(req.ownerName());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(ownerRole);
        user = farmUserRepository.save(user);

        Farm farm = new Farm();
        farm.setName(req.farmName());
        farm.setLocation(req.farmLocation());
        farm.setOwner(user);
        farm = farmRepository.save(farm);

        user.setFarm(farm);
        user = farmUserRepository.save(user);

        return buildLoginResponse(user);
    }

    public LoginResponse login(LoginRequest req) {
        boolean hasEmail = req.email() != null && !req.email().isBlank();
        boolean hasPhone = req.phone() != null && !req.phone().isBlank();
        if (!hasEmail && !hasPhone) {
            throw new IllegalArgumentException("Namba ya simu au barua pepe inahitajika.");
        }

        FarmUser user = (hasEmail ? farmUserRepository.findByEmail(req.email())
                                  : farmUserRepository.findByPhone(req.phone()))
                .orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Simu/barua pepe au password si sahihi.");
        }
        return buildLoginResponse(user);
    }

    /**
     * Hatua ya 1 ya forgot-password. Haitupi hitilafu wala haitofautishi
     * tabia kama namba haipo - AuthController inarudisha ujumbe generic kila
     * wakati, kuzuia mtu kugundua ni namba zipi zilizosajiliwa.
     */
    public void requestPasswordReset(ForgotPasswordRequest req) {
        farmUserRepository.findByPhone(req.phone()).ifPresent(passwordResetService::issueOtp);
    }

    /** Hatua ya 2: thibitisha OTP + weka password mpya (auto-login ikifanikiwa). */
    @Transactional
    public LoginResponse resetPassword(ResetPasswordRequest req) {
        FarmUser user = farmUserRepository.findByPhone(req.phone()).orElse(null);
        if (user == null || !passwordResetService.verifyAndConsume(user, req.otp())) {
            throw new IllegalArgumentException("Msimbo si sahihi, umeisha muda, au umeshatumika.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        farmUserRepository.save(user);

        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(FarmUser user) {
        // ROOT (kama Lsms) hana farm/role - ufikiaji wake unatoka kwenye isRoot
        // flag pekee, hivyo hahitaji shamba ili aweze kuingia.
        if (Boolean.TRUE.equals(user.getIsRoot())) {
            String token = jwtUtil.generateToken(user.getUserId(), null, null, "ROOT", true);
            return new LoginResponse(token,
                    new UserSummary(user.getUserId().toString(), user.getName(), "ROOT"));
        }

        if (user.getFarm() == null || user.getRole() == null) {
            throw new AccessDeniedException("Mtumiaji huyu hajaunganishwa na shamba lolote.");
        }

        // JWT haibebi orodha ya ruhusa - JwtAuthFilter inasoma ruhusa fresh
        // kutoka DB kwenye kila request (angalia JwtAuthFilter/PermissionChecker).
        String token = jwtUtil.generateToken(
                user.getUserId(), user.getFarm().getFarmId(), user.getRole().getRoleId(),
                user.getRole().getName(), false);

        return new LoginResponse(token,
                new UserSummary(user.getUserId().toString(), user.getName(), user.getRole().getName()));
    }
}
