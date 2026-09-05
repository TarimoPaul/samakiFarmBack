package com.samaki.farm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/users/{userId} - utambulisho wa mtu: jina, simu, barua pepe.
 *
 * HAIGUSI password, hali ya akaunti, wala uanachama. Kila kimoja kati ya
 * hivyo ni kitendo chake chenye endpoint yake, na kwa sababu ile ile
 * iliyofanya UpdateRoleRequest isiwe na ruhusa: ombi la kurekebisha herufi
 * moja ya jina halipaswi kuwa na uwezo wa kubadilisha kile mtu anachoweza
 * kufanya, wala kumzuia kuingia.
 *
 * `email` inaruhusiwa kuwa tupu/null - safu ni nullable. Ikiwa tupu
 * inahifadhiwa kama NULL, si "" (angalia UserService.updateUser).
 */
public record UpdateUserRequest(
        @NotBlank(message = "Jina linahitajika") String name,
        @NotBlank(message = "Namba ya simu inahitajika") String phone,
        @Email(message = "Email si sahihi") String email
) {}
