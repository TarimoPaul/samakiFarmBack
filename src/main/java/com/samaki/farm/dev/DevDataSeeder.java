package com.samaki.farm.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Kianzishi cha data ya MAENDELEO. Ni "wiring" pekee - logic yote iko
 * DevSeedService (sababu ile ile ya RbacDataInitializer: seed() inaitwa
 * KUPITIA proxy ya bean nyingine, hivyo @Transactional inafanya kazi kweli).
 *
 * =====================================================================
 * PROFILE YA `dev` PEKEE. Bean hii HAIPO kabisa bila profile hiyo, hivyo
 * hakuna njia ya kuiwasha kwa bahati mbaya kwenye production - hata kama
 * app.data.initialize=true. Uthibitisho: @Profile("dev") hapa NA kwenye
 * DevSeedService; Spring haitengenezi bean yoyote kati ya hizi mbili
 * wakati profile haipo.
 *
 *     mvn spring-boot:run -Dspring-boot.run.profiles=dev
 * =====================================================================
 *
 * Hii ni TOFAUTI na ROOT (RbacSeedService.seedRootUser): ROOT ni akaunti
 * ya kweli ya production inayotoka environment variables na HAINA password
 * inayojulikana. Hawa ni wahusika wa majaribio wenye password iliyoandikwa
 * kwenye repo kwa makusudi - ndiyo maana hawapaswi KAMWE kuwepo prod.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevDataSeeder.class);

    private final DevSeedService devSeedService;
    private final DevFeedSeedService devFeedSeedService;

    public DevDataSeeder(DevSeedService devSeedService, DevFeedSeedService devFeedSeedService) {
        this.devSeedService = devSeedService;
        this.devFeedSeedService = devFeedSeedService;
    }

    @Override
    public void run(String... args) {
        logger.warn("Profile ya 'dev' imewashwa: wahusika wa majaribio wenye password "
                + "inayojulikana wanatengenezwa. HII HAIPASWI KUWA PRODUCTION.");
        try {
            devSeedService.seed();
            // MPANGILIO NI MUHIMU: stoo ya chakula inahitaji shamba la
            // onyesho, ambalo seed() hapo juu ndiyo inalitengeneza.
            //
            // Ni wito wa PILI, si sehemu ya seed(), kwa sababu harness ya
            // majaribio inaita devSeedService.seed() moja kwa moja na
            // INAHITAJI kuanza bila chakula chochote - angalia javadoc ya
            // DevFeedSeedService.
            devFeedSeedService.seed();
        } catch (Exception e) {
            // Kama RbacDataInitializer: seeding kushindwa HAKUZUII app kuanza.
            logger.error("Dev seeding imeshindwa: {}", e.getMessage(), e);
        }
    }
}
