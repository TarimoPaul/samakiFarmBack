package com.samaki.farm.auth.dto;

import com.samaki.farm.farmuser.dto.UserSummary;

public record LoginResponse(String token, UserSummary user) {}
