package com.samaki.farm.auth.dto;

/**
 * Shamba MOJA ambalo mwombaji anaweza kulichagua - jibu la GET /api/auth/my-farms.
 *
 * Kiteuzi cha shamba kilikuwa kinasoma GET /api/farms, inayohitaji
 * manage_farms - sawa kwa ROOT, lakini mwanachama wa kawaida mwenye mashamba
 * mawili asingeweza kuiona. Hii ndiyo orodha yake.
 *
 * {@code role} ni jina la nafasi yake kwenye shamba hilo (null kama hana, na
 * null kwa ROOT ambaye hana uanachama wowote).
 */
public record MyFarm(Integer farmId, String name, String role) {}
