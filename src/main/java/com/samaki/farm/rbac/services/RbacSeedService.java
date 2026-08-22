package com.samaki.farm.rbac.services;

import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.entity.Permission;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.rbac.repository.PermissionRepository;
import com.samaki.farm.rbac.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Seeding ya RBAC (kama DataInitializer ya Lsms):
 *
 * 1. Permissions zinapakiwa kutoka seed/permissions.csv (idempotent - haziwekwi
 *    mara mbili, hivyo CSV inaweza kuongezewa mistari mipya wakati wowote).
 * 2. Role-permission mapping inapakiwa kutoka seed/role_permissions.csv MARA
 *    MOJA TU kwa kila role ambayo bado haina ruhusa yoyote (role iliyokwisha
 *    badilishwa na admin - hata ikiwa na ruhusa moja tu - haiguswi tena
 *    kwenye restart).
 * 3. Mtumiaji wa ROOT (isRoot=true, hana farm/role) anatengenezwa kama bado
 *    hayupo, kutoka app.root.* properties.
 *
 * Logic iko hapa (service) na si kwenye RbacDataInitializer (CommandLineRunner)
 * ili @Transactional ifanye kazi kweli: runner inaita seedAll() KUPITIA proxy
 * ya bean hii, hivyo transaction inafunguliwa kama inavyotakiwa. Ndani ya
 * seedAll(), method ndogo zinaitwa moja kwa moja (this.xxx) - kwa makusudi:
 * zote zinashiriki transaction ile ile, na hazina @Transactional yao ambayo
 * ingekuwa haina maana kwa self-invocation.
 */
@Service
public class RbacSeedService {

    private static final Logger logger = LoggerFactory.getLogger(RbacSeedService.class);

    @Value("${app.permissions.csv.path:seed/permissions.csv}")
    private String permissionsCsvPath;

    @Value("${app.role-permissions.csv.path:seed/role_permissions.csv}")
    private String rolePermissionsCsvPath;

    @Value("${app.root.name:System Root}")
    private String rootName;

    @Value("${app.root.phone:0000000000}")
    private String rootPhone;

    @Value("${app.root.email:root@system.com}")
    private String rootEmail;

    @Value("${app.root.password:ChangeMe@123}")
    private String rootPassword;

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final FarmUserRepository farmUserRepository;
    private final PasswordEncoder passwordEncoder;

    public RbacSeedService(PermissionRepository permissionRepository, RoleRepository roleRepository,
                            FarmUserRepository farmUserRepository, PasswordEncoder passwordEncoder) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.farmUserRepository = farmUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void seedAll() {
        seedPermissions();
        seedRolePermissions();
        seedRootUser();
    }

    private void seedPermissions() {
        List<String[]> rows = readCsvRows(permissionsCsvPath);
        int created = 0;

        for (String[] row : rows) {
            if (row.length < 2) continue;
            String code = row[0];
            String module = row.length > 1 ? row[1] : "FARM";
            String group = row.length > 2 ? row[2] : null;
            String description = row.length > 3 ? row[3] : null;

            if (!permissionRepository.existsByCode(code)) {
                Permission permission = new Permission();
                permission.setCode(code);
                permission.setModule(module);
                permission.setGroupName(group);
                permission.setDescription(description);
                permissionRepository.save(permission);
                created++;
            }
        }

        if (created > 0) {
            logger.info("Permissions {} mpya zimeongezwa kutoka CSV", created);
            JwtAuthFilter.clearRootCache();
        }
    }

    private void seedRolePermissions() {
        List<String[]> rows = readCsvRows(rolePermissionsCsvPath);
        Map<String, List<String>> byRole = new LinkedHashMap<>();
        for (String[] row : rows) {
            if (row.length < 2) continue;
            byRole.computeIfAbsent(row[0], k -> new ArrayList<>()).add(row[1]);
        }

        int rolesSeeded = 0;
        int rolesSkipped = 0;
        int assigned = 0;

        for (Map.Entry<String, List<String>> entry : byRole.entrySet()) {
            Optional<Role> roleOpt = roleRepository.findByName(entry.getKey());
            if (roleOpt.isEmpty()) {
                continue; // role haijatengenezwa bado - itapewa ruhusa itakapotengenezwa
            }

            Role role = roleOpt.get();
            if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                rolesSkipped++;
                continue; // role tayari ime-configuriwa - usiiguse tena kwenye restart
            }

            Set<Permission> perms = new HashSet<>();
            for (String code : entry.getValue()) {
                permissionRepository.findByCode(code).ifPresent(perms::add);
            }
            if (!perms.isEmpty()) {
                role.setPermissions(perms);
                roleRepository.save(role);
                assigned += perms.size();
                rolesSeeded++;
            }
        }

        if (assigned > 0) {
            logger.info("Ruhusa {} zimewekwa kwa role {} mpya (first-run); role {} zilizokwisha-configuriwa ziliachwa",
                    assigned, rolesSeeded, rolesSkipped);
            JwtAuthFilter.clearRootCache();
            JwtAuthFilter.clearAllUserCache();
        }
    }

    private void seedRootUser() {
        Optional<FarmUser> existing = farmUserRepository.findByPhone(rootPhone);
        if (existing.isPresent()) {
            FarmUser root = existing.get();
            boolean changed = false;
            if (!Boolean.TRUE.equals(root.getIsRoot())) {
                root.setIsRoot(true);
                changed = true;
            }
            if (root.getEmail() == null || root.getEmail().isBlank()) {
                root.setEmail(rootEmail);
                changed = true;
            }
            if (changed) {
                farmUserRepository.save(root);
                logger.info("Mtumiaji wa ROOT aliyepo amesasishwa (isRoot/email): {}", rootPhone);
            }
            return;
        }

        // ROOT anabaki bila farm/role (zote ni NULLable) - ufikiaji wake
        // unatoka kwenye isRoot flag pekee, si uhusiano wowote.
        FarmUser root = new FarmUser();
        root.setName(rootName);
        root.setPhone(rootPhone);
        root.setEmail(rootEmail);
        root.setPasswordHash(passwordEncoder.encode(rootPassword));
        root.setStatus("ACTIVE");
        root.setIsRoot(true);
        // createdAt haiwekwi kwa mkono tena - @CreatedDate (AuditingConfig)
        // ndiyo inayoiweka, na ingeandika juu ya thamani yoyote ya hapa.
        farmUserRepository.save(root);
        logger.info("Mtumiaji wa ROOT ametengenezwa: {}", rootPhone);
    }

    private List<String[]> readCsvRows(String path) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = openCsv(path)) {
            String content = new String(in.readAllBytes());
            String[] lines = content.split("\n");
            for (int i = 1; i < lines.length; i++) { // mstari wa kwanza ni header
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = Arrays.stream(line.split(","))
                        .map(s -> s.trim().replace("\"", ""))
                        .toArray(String[]::new);
                rows.add(parts);
            }
        } catch (Exception e) {
            logger.warn("Imeshindwa kusoma CSV {}: {}", path, e.getMessage());
        }
        return rows;
    }

    private InputStream openCsv(String path) throws Exception {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (Exception e) {
            return new FileInputStream(path);
        }
    }
}
