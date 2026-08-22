package com.samaki.farm.auth.dto;

import com.samaki.farm.user.dto.UserSummary;

/**
 * mustChangePassword: ikiwa true, mteja AMEingia kikamilifu (token ni
 * halali) lakini lazima aelekezwe kubadilisha password kabla ya kuendelea.
 * Inatumika hasa kwa ROOT anayetengenezwa kutoka environment variable.
 */
public record LoginResponse(String token, UserSummary user, boolean mustChangePassword) {}
