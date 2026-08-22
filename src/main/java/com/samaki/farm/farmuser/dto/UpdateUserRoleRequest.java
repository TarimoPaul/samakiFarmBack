package com.samaki.farm.farmuser.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull(message = "roleId inahitajika") Integer roleId) {}
