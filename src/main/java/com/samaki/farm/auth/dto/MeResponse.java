package com.samaki.farm.auth.dto;

import java.util.List;

/**
 * "Mimi ni nani, na naruhusiwa kufanya nini" - jibu la GET /api/auth/me.
 *
 * `permissions` ndiyo sababu ya kuwepo kwa endpoint hii. Login inarudisha
 * JINA la role pekee, ilhali RBAC hapa ni ya RUHUSA: ruhusa za role
 * zinaweza kubadilishwa wakati wowote kupitia
 * PUT /api/roles/{id}/permissions. UI inayoficha vitufe kwa jina la role
 * ("kama ni WORKER, ficha X") inakuwa imepitwa na wakati mara ya kwanza
 * role hiyo inapohaririwa - hivyo lazima itawi kwa misimbo ya ruhusa
 * halisi (angalia FRONTEND_BACKEND_AUDIT.md, uamuzi #4).
 *
 * Sehemu nyingine ni zilezile za UserSummary, ili skrini zisilazimike
 * kuunganisha majibu mawili.
 *
 * {@code farmId} ni shamba LINALOTUMIKA sasa hivi - likiwemo lile ROOT
 * alilochagua kwa kichwa X-Farm-Id (angalia JwtAuthFilter.withSelectedFarm).
 * Hivyo mteja anaweza kuliamini kama jibu la "ombi langu linafanyiwa kazi
 * kwenye shamba gani", si kama nakala ya uanachama wake.
 *
 * {@code canSelectFarm} ni ruhusa ya kuchagua shamba asilolimiliki. UI haiwezi
 * kuipata yenyewe: mara ROOT anapochagua shamba, farmId yake huacha kuwa
 * null na kigezo cha "hana shamba" hakitofautishi tena ROOT na mwanachama
 * wa kawaida - kisha kiteuzi kingetoweka mara tu kinapotumika.
 */
public record MeResponse(String id, String name, String phone, String status,
                          Integer farmId, String role, List<String> permissions,
                          boolean canSelectFarm) {}
