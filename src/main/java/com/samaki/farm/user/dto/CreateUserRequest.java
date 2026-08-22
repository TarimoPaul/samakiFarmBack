package com.samaki.farm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Msimamizi anaunda mtumiaji moja kwa moja (inahitaji manage_users).
 * Tofauti na RegisterRequest: mtu aliyeundwa na msimamizi anaanza akiwa
 * ACTIVE - msimamizi ndiye idhini yenyewe, hakuna haja ya kuidhinishwa tena.
 *
 * Uanachama (shamba + role) HAUTOLEWI hapa - ni hatua tofauti
 * (AssignMembershipRequest), kwa sababu idhini na role ni vitu viwili
 * tofauti (Part A #4).
 */
public record CreateUserRequest(
        @NotBlank(message = "Jina linahitajika") String name,
        @NotBlank(message = "Namba ya simu inahitajika") String phone,
        @Email(message = "Email si sahihi") String email,
        @NotBlank(message = "Password inahitajika")
        @Size(min = 6, message = "Password iwe angalau herufi 6") String password
) {}
