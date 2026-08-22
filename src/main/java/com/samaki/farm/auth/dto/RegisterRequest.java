package com.samaki.farm.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Kujisajili mwenyewe. Inabadilisha SignupRequest ya zamani, ambayo
 * ilikuwa inaunda shamba + mmiliki + token kwa ombi moja.
 *
 * HAKUNA farmName wala roleId hapa KWA MAKUSUDI: kujisajili
 * hakutengenezi shamba, hakutoi role, na hakutoi token. Mtu anatengenezwa
 * akiwa PENDING_APPROVAL na kusubiri idhini.
 */
public record RegisterRequest(
        @NotBlank(message = "Jina linahitajika") String name,
        @NotBlank(message = "Namba ya simu inahitajika") String phone,
        @Email(message = "Email si sahihi") String email,
        @NotBlank(message = "Password inahitajika")
        @Size(min = 6, message = "Password iwe angalau herufi 6") String password
) {}
