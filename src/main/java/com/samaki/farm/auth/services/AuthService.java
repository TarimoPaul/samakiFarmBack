package com.samaki.farm.auth.services;

import com.samaki.farm.auth.dto.ForgotPasswordRequest;
import com.samaki.farm.auth.dto.LoginRequest;
import com.samaki.farm.auth.dto.LoginResponse;
import com.samaki.farm.auth.dto.RegisterRequest;
import com.samaki.farm.auth.dto.RegistrationResponse;
import com.samaki.farm.auth.dto.ResetPasswordRequest;
import com.samaki.farm.auth.security.JwtUtil;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.common.exception.ForbiddenException;
import com.samaki.farm.common.exception.UnauthorizedException;
import com.samaki.farm.common.ratelimit.RateLimiter;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.user.dto.UserSummary;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import com.samaki.farm.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Kujisajili, kuingia, na kubadilisha password.
 *
 * Hitilafu ni exceptions (si ResponseEntity) ili service hii isijue chochote
 * kuhusu HTTP - GlobalExceptionHandler ndiyo inayoziweka status + errorCode.
 */
@Service
public class AuthService {

    // Kikomo cha maombi (angalia RateLimiter kwa mipaka yake)
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);
    private static final int MAX_REGISTRATIONS = 5;
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

    /**
     * Hash halali ya BCrypt isiyolingana na password yoyote inayojulikana.
     * Inatumika pale mtu HAYUPO, ili muda wa kujibu uwe sawa na wa mtu
     * aliyepo - vinginevyo tofauti ya muda ingefichua ni namba zipi
     * zilizosajiliwa (timing-based user enumeration).
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final FarmUserRepository farmUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;

    public AuthService(UserRepository userRepository, FarmUserRepository farmUserRepository,
                        PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                        PasswordResetService passwordResetService, RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.farmUserRepository = farmUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * B3 - kujisajili mwenyewe. HAKUNA shamba, HAKUNA uanachama, HAKUNA token.
     * Mtu anatengenezwa PENDING_APPROVAL na kusubiri mwenye ruhusa ya
     * approve_users amruhusu.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest req, String clientIp) {
        rateLimiter.check("register:" + clientIp, MAX_REGISTRATIONS, REGISTRATION_WINDOW);

        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("Namba ya simu hii tayari imesajiliwa.");
        }
        if (req.email() != null && !req.email().isBlank() && userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Barua pepe hii tayari imesajiliwa.");
        }

        User user = new User();
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(UserStatus.PENDING_APPROVAL);
        user = userRepository.save(user);

        return new RegistrationResponse(user.getUserId().toString(), user.getStatus().name());
    }

    /**
     * B6 - password inathibitishwa KWANZA, kisha hali ya akaunti inaangaliwa.
     *
     * Mpangilio huu ndio muhimu: kurudisha 403 PENDING_APPROVAL kabla ya
     * kuthibitisha password kungemruhusu mtu yeyote kugundua ni namba zipi
     * zilizosajiliwa. Baada ya password kuthibitishwa, mwombaji tayari
     * anajua akaunti ipo - hivyo kumweleza sababu halisi hakufichui kitu.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req, String clientIp) {
        rateLimiter.check("login:" + clientIp, MAX_LOGIN_ATTEMPTS, LOGIN_WINDOW);

        boolean hasEmail = req.email() != null && !req.email().isBlank();
        boolean hasPhone = req.phone() != null && !req.phone().isBlank();
        if (!hasEmail && !hasPhone) {
            throw new IllegalArgumentException("Namba ya simu au barua pepe inahitajika.");
        }

        User user = (hasEmail ? userRepository.findByEmail(req.email())
                              : userRepository.findByPhone(req.phone()))
                .orElse(null);

        // HATUA 1 - password. Mtu asiyejulikana au aliyefutwa anapata jibu
        // lile lile la 401 (na muda ule ule, shukrani kwa DUMMY_HASH).
        String hash = user == null ? DUMMY_HASH : user.getPasswordHash();
        boolean passwordOk = passwordEncoder.matches(req.password(), hash);
        if (user == null || !passwordOk) {
            throw new UnauthorizedException("Simu/barua pepe au password si sahihi.",
                    ErrorCodes.INVALID_CREDENTIALS);
        }

        // HATUA 2 - hali ya akaunti. Sasa tunaweza kueleza sababu halisi.
        switch (user.getStatus()) {
            case PENDING_APPROVAL -> throw new ForbiddenException(
                    "Akaunti yako bado haijaidhinishwa. Subiri msimamizi akuruhusu.",
                    ErrorCodes.PENDING_APPROVAL);
            case DISABLED -> throw new ForbiddenException(
                    "Akaunti yako imezuiwa. Wasiliana na msimamizi.",
                    ErrorCodes.ACCOUNT_DISABLED);
            case ACTIVE -> { /* inaendelea hapa chini */ }
        }

        rateLimiter.reset("login:" + clientIp);
        return buildLoginResponse(user);
    }

    /**
     * Jibu ni generic kila wakati (AuthController), hivyo hakuna
     * exception hapa hata kama namba haipo - kuzuia user enumeration.
     */
    public void requestPasswordReset(ForgotPasswordRequest req) {
        userRepository.findByPhone(req.phone()).ifPresent(passwordResetService::issueOtp);
    }

    /** Thibitisha OTP + weka password mpya. Inaondoa pia lazima ya kubadilisha password. */
    @Transactional
    public LoginResponse resetPassword(ResetPasswordRequest req) {
        User user = userRepository.findByPhone(req.phone()).orElse(null);
        if (user == null || !passwordResetService.verifyAndConsume(user, req.otp())) {
            throw new IllegalArgumentException("Msimbo si sahihi, umeisha muda, au umeshatumika.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Hali ya akaunti inakaguliwa hapa pia: kubadilisha password
        // HAKUMRUHUSU mtu asiyeidhinishwa kuingia.
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException(
                    "Password imebadilishwa, lakini akaunti yako bado haijaidhinishwa.",
                    user.getStatus() == UserStatus.PENDING_APPROVAL
                            ? ErrorCodes.PENDING_APPROVAL : ErrorCodes.ACCOUNT_DISABLED);
        }
        return buildLoginResponse(user);
    }

    /**
     * B5 - token inabeba uanachama MMOJA. Mtu mwenye uanachama zaidi ya
     * mmoja anapewa wa shamba lenye farmId ndogo zaidi (thabiti kila
     * wakati, si nasibu).
     */
    private LoginResponse buildLoginResponse(User user) {
        // ROOT hana uanachama - ufikiaji wake unatoka kwenye isRoot flag.
        if (Boolean.TRUE.equals(user.getIsRoot())) {
            String token = jwtUtil.generateToken(user.getUserId(), null, null, "ROOT", true);
            return new LoginResponse(token,
                    new UserSummary(user.getUserId().toString(), user.getName(), user.getPhone(),
                            user.getStatus().name(), null, "ROOT"),
                    user.isMustChangePassword());
        }

        // TODO: farm switching - kwa sasa uanachama wa kwanza pekee.
        List<FarmUser> memberships =
                farmUserRepository.findByUser_UserIdOrderByFarm_FarmIdAsc(user.getUserId());

        // Mtu ALIYEIDHINISHWA asiye na uanachama ANARUHUSIWA kuingia (Part A #4):
        // anapata token isiyo na farm/role, na frontend inaonyesha ukurasa
        // wa "bado hujapangiwa shamba wala role".
        if (memberships.isEmpty()) {
            String token = jwtUtil.generateToken(user.getUserId(), null, null, null, false);
            return new LoginResponse(token,
                    new UserSummary(user.getUserId().toString(), user.getName(), user.getPhone(),
                            user.getStatus().name(), null, null),
                    user.isMustChangePassword());
        }

        FarmUser membership = memberships.get(0);
        Integer roleId = membership.getRole() == null ? null : membership.getRole().getRoleId();
        String roleName = membership.getRole() == null ? null : membership.getRole().getName();

        // JWT haibebi orodha ya ruhusa - JwtAuthFilter inaisoma fresh kutoka
        // DB kila request, hivyo mabadiliko ya role hayahitaji login upya.
        String token = jwtUtil.generateToken(user.getUserId(), membership.getFarm().getFarmId(),
                roleId, roleName, false);

        return new LoginResponse(token,
                new UserSummary(user.getUserId().toString(), user.getName(), user.getPhone(),
                        user.getStatus().name(), membership.getFarm().getFarmId(), roleName),
                user.isMustChangePassword());
    }
}
