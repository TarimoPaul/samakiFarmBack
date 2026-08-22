package com.samaki.farm.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Anwani ya IP ya mwombaji, kwa ajili ya rate limiting.
 *
 * X-Forwarded-For inaheshimiwa kwa sababu app inakusudiwa kukaa nyuma ya
 * load balancer (ALB kwenye ECS - angalia README). ONYO: header hii
 * inaweza kudanganywa na mteja ikiwa app itafikiwa MOJA KWA MOJA bila
 * proxy. Hakikisha proxy inaiandika upya kabla ya production.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // "client, proxy1, proxy2" - ya kwanza ndiyo ya mteja
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
