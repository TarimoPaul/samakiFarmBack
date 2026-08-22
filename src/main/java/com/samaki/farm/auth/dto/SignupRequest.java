package com.samaki.farm.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Signup ya farm + owner mpya (bila kuhitaji auth ya awali) - tofauti na
 * CreateUserRequest (hiyo inahitaji manage_users, ni "admin anaongeza
 * mfanyakazi kwenye farm iliyopo tayari"). Hii ni njia PEKEE ya kuunda Farm
 * kupitia API. Role inayopewa ni OWNER kila wakati (hardcoded ndani ya
 * AuthService, si kutoka client), na isRoot haiguswi kabisa - hazitolewi
 * kama sehemu za request.
 */
public record SignupRequest(
        @NotBlank(message = "Jina la shamba linahitajika") String farmName,
        String farmLocation,
        @NotBlank(message = "Jina la mmiliki linahitajika") String ownerName,
        @NotBlank(message = "Namba ya simu inahitajika") String phone,
        @Email(message = "Email si sahihi") String email,
        @NotBlank(message = "Password inahitajika")
        @Size(min = 6, message = "Password iwe angalau herufi 6") String password
) {}
