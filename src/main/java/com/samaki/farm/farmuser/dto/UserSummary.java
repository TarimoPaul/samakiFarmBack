package com.samaki.farm.farmuser.dto;

/**
 * Muhtasari salama wa FarmUser kwa mteja - entity yenyewe HAIRUDISHWI kamwe:
 * ingebeba passwordHash, na FarmUser.farm -> Farm.owner -> FarmUser ni
 * mzunguko ambao Jackson isingeweza kuumaliza.
 *
 * Iko ndani ya module ya farmuser (si auth) kwa sababu inaelezea FarmUser -
 * auth inaitumia tu kama sehemu ya LoginResponse.
 */
public record UserSummary(String id, String name, String role) {}
