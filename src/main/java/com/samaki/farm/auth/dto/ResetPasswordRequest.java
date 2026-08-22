package com.samaki.farm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Namba ya simu inahitajika") String phone,
        @NotBlank(message = "Msimbo (OTP) unahitajika") String otp,
        @NotBlank(message = "Password mpya inahitajika")
        @Size(min = 6, message = "Password iwe angalau herufi 6") String newPassword
) {}
