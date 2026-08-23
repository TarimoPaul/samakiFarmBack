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
 */
public record MeResponse(String id, String name, String phone, String status,
                          Integer farmId, String role, List<String> permissions) {}
