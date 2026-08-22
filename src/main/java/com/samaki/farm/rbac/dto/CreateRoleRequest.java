package com.samaki.farm.rbac.dto;

import java.util.List;

public record CreateRoleRequest(String name, String description, List<Integer> permissionIds) {}
