package com.samaki.farm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank(message = "Namba ya simu inahitajika") String phone) {}
