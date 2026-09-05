package com.samaki.farm.rbac.dto;

/**
 * PUT /api/roles/{roleId} - jina na maelezo PEKEE.
 *
 * Ruhusa HAZIMO kwa makusudi: zina endpoint yao
 * (PUT /api/roles/{roleId}/permissions) yenye sheria ya yote-au-hakuna
 * ambayo ombi la kubadilisha jina halipaswi kuiingilia. Orodha tupu ya
 * ruhusa iliyotumwa hapa kwa bahati mbaya ingefuta sera nzima ya usalama
 * ya nafasi ilhali mtumiaji alikusudia kurekebisha herufi moja ya jina.
 */
public record UpdateRoleRequest(String name, String description) {}
