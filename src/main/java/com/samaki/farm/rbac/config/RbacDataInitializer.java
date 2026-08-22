package com.samaki.farm.rbac.config;

import com.samaki.farm.rbac.services.RbacSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Kianzishi cha seeding ya RBAC wakati app inapoanza. Hii ni "wiring" pekee -
 * logic yote iko RbacSeedService.
 *
 * Kuipitia service (badala ya kuwa na logic hapa) ndiko kunakofanya
 * @Transactional ifanye kazi kweli: seedAll() inaitwa kupitia proxy ya bean
 * nyingine, si self-invocation - hivyo transaction inafunguliwa, na
 * role.getPermissions() (@ManyToMany LAZY) inasomeka bila
 * LazyInitializationException.
 */
@Component
public class RbacDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RbacDataInitializer.class);

    @Value("${app.data.initialize:true}")
    private boolean shouldInitialize;

    private final RbacSeedService rbacSeedService;

    public RbacDataInitializer(RbacSeedService rbacSeedService) {
        this.rbacSeedService = rbacSeedService;
    }

    @Override
    public void run(String... args) {
        if (!shouldInitialize) {
            return;
        }
        try {
            rbacSeedService.seedAll();
        } catch (Exception e) {
            // Seeding kushindwa HAKUZUII app kuanza - inaweza kuwa CSV
            // haipatikani tu; logi ndiyo ishara ya kuchunguza.
            logger.error("RBAC seeding imeshindwa: {}", e.getMessage(), e);
        }
    }
}
