package com.samaki.farm.rbac.repository;

import com.samaki.farm.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
