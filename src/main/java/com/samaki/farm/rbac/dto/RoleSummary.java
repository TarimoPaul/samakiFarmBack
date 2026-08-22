package com.samaki.farm.rbac.dto;

import java.util.List;

public record RoleSummary(Integer roleId, String name, String description, List<String> permissions) {}
