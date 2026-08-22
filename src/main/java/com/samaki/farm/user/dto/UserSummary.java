package com.samaki.farm.user.dto;

/**
 * Muhtasari salama wa mtumiaji. Entity HAIRUDISHWI kamwe - ingebeba
 * passwordHash.
 *
 * `role` na `farmId` vinatoka kwenye UANACHAMA, si kwa mtu - vinaweza kuwa
 * null kwa mtu aliyeidhinishwa ambaye bado hajapewa shamba (hali halali,
 * angalia UserStatus).
 */
public record UserSummary(String id, String name, String phone, String status,
                           Integer farmId, String role) {}
