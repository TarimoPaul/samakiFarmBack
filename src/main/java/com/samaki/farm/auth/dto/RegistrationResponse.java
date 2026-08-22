package com.samaki.farm.auth.dto;

/**
 * Jibu la kujisajili. HAKUNA token hapa - mtu bado hajaidhinishwa.
 * `status` inarudishwa ili frontend ijue ionyeshe ukurasa gani.
 */
public record RegistrationResponse(String userId, String status) {}
