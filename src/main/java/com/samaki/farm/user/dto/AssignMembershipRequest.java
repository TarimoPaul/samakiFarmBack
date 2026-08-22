package com.samaki.farm.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Kumpa mtu uanachama wa shamba pamoja na role yake hapo.
 *
 * roleId inaruhusiwa kuwa null: mtu anaweza kuwekwa kwenye shamba bila role
 * bado. Ataingia na kuona ukurasa mtupu hadi apewe role.
 */
public record AssignMembershipRequest(
        @NotNull(message = "farmId inahitajika") Integer farmId,
        Integer roleId
) {}
