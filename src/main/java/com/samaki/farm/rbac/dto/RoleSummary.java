package com.samaki.farm.rbac.dto;

import java.util.List;

/**
 * Nafasi kama mteja anavyoiona.
 *
 * `active` ipo hapa kwa sababu GET /api/roles inarudisha nafasi
 * ZILIZOZIMWA pia: skrini ya wasimamizi lazima iziona ili iweze
 * kuzirudisha, ilhali kichagua-nafasi cha skrini ya wanachama kinapaswa
 * kutoa zilizo hai pekee. Endpoint moja, mteja anachuja.
 */
public record RoleSummary(Integer roleId, String name, String description, boolean active,
                          List<String> permissions) {}
