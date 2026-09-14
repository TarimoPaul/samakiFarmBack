package com.samaki.farm.farmuser.dto;

/**
 * Uanachama MMOJA wa mtu: shamba gani, na nafasi gani hapo.
 *
 * Jibu la GET /api/users/{userId}/memberships - skrini ya Members inalihitaji
 * ili msimamizi aone mtu yuko kwenye mashamba gani kabla ya kumpa jingine.
 * UserSummary haiwezi kujibu hili: inabeba shamba MOJA tu.
 *
 * roleId/roleName vinaweza kuwa null - uanachama bila role ni halali.
 */
public record MembershipView(Integer farmId, String farmName, Integer roleId, String roleName) {}
