package com.samaki.farm.auth.controller;

import com.samaki.farm.auth.dto.ForgotPasswordRequest;
import com.samaki.farm.auth.dto.LoginRequest;
import com.samaki.farm.auth.dto.LoginResponse;
import com.samaki.farm.auth.dto.ResetPasswordRequest;
import com.samaki.farm.auth.dto.SignupRequest;
import com.samaki.farm.auth.services.AuthService;
import com.samaki.farm.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * REST pekee (kama ilivyoamuliwa) - Auth. Kila kitu kingine cha uzalishaji
 * (Production Units, Cycles, n.k.) ni GraphQL.
 *
 * Controller hii ni HTTP tu: kupokea request, kuita service, kufunga jibu
 * kwenye ApiResponse. Hitilafu hazishughulikiwi hapa - service inatupa
 * exceptions, na GlobalExceptionHandler inazibadilisha kuwa status codes
 * (409/401/400/403).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ApiResponse<LoginResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ApiResponse.ok(authService.signup(req));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    /**
     * Jibu ni generic KILA WAKATI (bila kujali kama phone ipo mfumo-ni au
     * la) - kuzuia mtu kugundua ni namba zipi zilizosajiliwa (user
     * enumeration).
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
