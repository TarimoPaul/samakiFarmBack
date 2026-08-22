package com.samaki.farm.auth.controller;

import com.samaki.farm.auth.dto.ForgotPasswordRequest;
import com.samaki.farm.auth.dto.LoginRequest;
import com.samaki.farm.auth.dto.LoginResponse;
import com.samaki.farm.auth.dto.RegisterRequest;
import com.samaki.farm.auth.dto.RegistrationResponse;
import com.samaki.farm.auth.dto.ResetPasswordRequest;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
