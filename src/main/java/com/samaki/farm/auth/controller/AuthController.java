package com.samaki.farm.auth.controller;

import com.samaki.farm.auth.dto.ChangePasswordRequest;
import com.samaki.farm.auth.dto.ForgotPasswordRequest;
import com.samaki.farm.auth.dto.LoginRequest;
import com.samaki.farm.auth.dto.LoginResponse;
import com.samaki.farm.auth.dto.MeResponse;
import com.samaki.farm.auth.dto.RegisterRequest;
import com.samaki.farm.auth.dto.RegistrationResponse;
import com.samaki.farm.auth.dto.ResetPasswordRequest;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.auth.services.AuthService;
import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * REST pekee - Auth. Controller hii ni HTTP tu: kupokea request, kuita
 * service, kufunga jibu kwenye ApiResponse. Hitilafu hazishughulikiwi
 * hapa - service inatupa exceptions, na GlobalExceptionHandler
 * inazibadilisha kuwa status + errorCode (401/403/409/429).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PermissionChecker permissionChecker;

    public AuthController(AuthService authService, PermissionChecker permissionChecker) {
        this.authService = authService;
        this.permissionChecker = permissionChecker;
    }

    /**
     * B3 - kujisajili. Inabadilisha /signup ya zamani iliyokuwa inaunda
     * shamba + mmiliki + token kwa ombi moja. Hapa: mtu pekee, hali
     * PENDING_APPROVAL, hakuna token.
     *
     * Endpoint hii ni wazi kwa mtu yeyote mwenye URL - ndiyo maana ina
     * rate limiting (angalia AuthService.register).
     */
    @PostMapping("/register")
    public ApiResponse<RegistrationResponse> register(@Valid @RequestBody RegisterRequest req,
                                                       HttpServletRequest http) {
        RegistrationResponse result = authService.register(req, ClientIp.of(http));
        return ApiResponse.ok(result,
                "Usajili umepokelewa. Subiri msimamizi aidhinishe akaunti yako.");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, ClientIp.of(http)));
    }

    /**
     * Jibu ni generic KILA WAKATI (bila kujali kama namba ipo mfumo-ni au
     * la) - kuzuia mtu kugundua ni namba zipi zilizosajiliwa.
     */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.requestPasswordReset(req);
        return ApiResponse.ok(null,
                "Kama namba hii ipo kwenye mfumo, msimbo wa uthibitisho (OTP) umetumwa kwa SMS.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<LoginResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return ApiResponse.ok(authService.resetPassword(req));
    }

    /**
     * B8 - kubadilisha password ukiwa umeingia (HAKUNA OTP/SMS).
     *
     * Hii ndiyo endpoint PEKEE inayoruhusiwa pale `must_change_password`
     * ni true - angalia JwtAuthFilter. SecurityConfig inaitaka token
     * halali hapa, tofauti na /api/auth/** nyingine zilizo wazi.
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(permissionChecker.currentUser().getUserId(), req);
        return ApiResponse.ok(null, "Password imebadilishwa.");
    }

    /**
     * Mtumiaji wa sasa + RUHUSA zake halisi.
     *
     * Login inarudisha JINA la role pekee, lakini UI inahitaji kujua ni
     * vitufe vipi vya kuonyesha - na ruhusa za role zinaweza kuhaririwa
     * wakati wowote (PUT /api/roles/{id}/permissions), hivyo jina la role
     * si chanzo cha ukweli. Endpoint hii ndiyo chanzo hicho.
     *
     * Inahitaji token halali (angalia SecurityConfig), lakini HAIZUIWI na
     * must_change_password: iko chini ya /api/auth/**, hivyo hata skrini
     * ya kubadilisha password inaweza kuonyesha jina la mtumiaji.
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.describeCurrentUser(permissionChecker.currentUser()));
    }
}
