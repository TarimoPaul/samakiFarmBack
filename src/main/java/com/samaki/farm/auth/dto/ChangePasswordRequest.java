package com.samaki.farm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kubadilisha password ukiwa UMEINGIA (una token halali).
 *
 * Tofauti na ResetPasswordRequest: hii HAIHITAJI OTP/SMS. Uthibitisho ni
 * password ya sasa + token - hivyo ROOT anayetengenezwa kutoka environment
 * variable anaweza kuondoa `must_change_password` hata kama huduma ya SMS
 * haijawekwa bado.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Password ya sasa inahitajika") String currentPassword,
        @NotBlank(message = "Password mpya inahitajika")
        @Size(min = 6, message = "Password iwe angalau herufi 6") String newPassword
) {}
