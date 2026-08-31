package com.samaki.farm.dev;

import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.rbac.repository.RoleRepository;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import com.samaki.farm.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Wahusika wa majaribio wanaodumu, kwa PROFILE YA `dev` PEKEE.
 *
 * =====================================================================
 * WHY THIS EXISTS
 *
 * Kila ukaguzi uliopita ulitengeneza wahusika wake kwa mkono kupitia
 * register -> approve -> memberships, kisha ukawafuta. Matokeo: kila
 * ripoti ina password ambazo hazifanyi kazi tena, na ukaguzi unaofuata
 * hauwezi kuthibitisha chochote kwa vitendo (angalia D4_AUDIT_REPORT.md
 * ya frontend: "the D-4 harness principals were deliberately removed").
 *
 * Hawa wanarudi kwenye kila `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
 *
 * PASSWORD YA WOTE: Dev@12345   <- imeandikwa hapa kwa MAKUSUDI
 *
 * Ndiyo sababu hasa @Profile("dev") ipo: password inayojulikana na kila
 * anayesoma repo ni salama kwenye laptop ya maendeleo pekee. Bila profile
 * hiyo bean hii HAITENGENEZWI, hivyo hakuna mtumiaji hata mmoja kati ya
 * hawa anayeingia kwenye database ya production.
 *
 * Tofautisha na ROOT (RbacSeedService.seedRootUser): yule ni akaunti ya
 * kweli, password yake inatoka environment variables, na haiguswi hapa.
 * =====================================================================
 *
 * MASHAMBA MAWILI kwa makusudi. Shamba moja haliwezi kuthibitisha D-1
 * (kuvuja kati ya mashamba): ili kuonyesha kwamba mtu wa shamba A
 * amezuiwa kufikia data ya shamba B, ni lazima shamba B liwepo lenye
 * data yake. Kila shamba lina kitengo chake, na A lina mzunguko.
 *
 * MUST_CHANGE_PASSWORD ni false kwa hawa - tofauti na ROOT. Lango la
 * kubadilisha password lingezuia kila ombi la curl kwenye majaribio,
 * ilhali lengo lao ni kupimwa moja kwa moja.
 */
@Service
@Profile("dev")
public class DevSeedService {

    private static final Logger logger = LoggerFactory.getLogger(DevSeedService.class);

    /** Password ya wahusika WOTE wa dev. Imeandikwa wazi - angalia javadoc. */
    public static final String DEV_PASSWORD = "Dev@12345";

    private static final String FARM_A = "Dev Farm A";
    private static final String FARM_B = "Dev Farm B";

    /**
     * Wahusika: simu, jina, role.
     *
     * Role `null` ina maana uanachama BILA role - ndiye pekee anayeweza
     * kufanya query ya kawaida ijibu FORBIDDEN, kwa sababu role zote nne
     * zilizopandwa zina view_dashboard.
     */
    private static final String ADMIN_PHONE = "0700100001";
    private static final String WORKER_PHONE = "0700100002";
    private static final String VIEWER_PHONE = "0700100003";
    private static final String NOROLE_PHONE = "0700100004";
    private static final String WORKER_B_PHONE = "0700100005";

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final FarmUserRepository farmUserRepository;
    private final RoleRepository roleRepository;
    private final ProductionUnitRepository unitRepository;
    private final CycleRepository cycleRepository;
    private final SpeciesRepository speciesRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSeedService(UserRepository userRepository, FarmRepository farmRepository,
                           FarmUserRepository farmUserRepository, RoleRepository roleRepository,
                           ProductionUnitRepository unitRepository, CycleRepository cycleRepository,
                           SpeciesRepository speciesRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.cycleRepository = cycleRepository;
        this.speciesRepository = speciesRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Idempotent kama RbacSeedService: kila kitu ni create-if-not-exists,
     * hivyo restart HAIFUTI wala HAIRUDISHI kitu ulichobadilisha kwa mkono
     * wakati wa majaribio.
     */
    @Transactional
    public void seed() {
        Farm farmA = farmByName(FARM_A, "Dev-Land A");
        Farm farmB = farmByName(FARM_B, "Dev-Land B");

        User admin = user(ADMIN_PHONE, "Dev Admin");
        User worker = user(WORKER_PHONE, "Dev Worker");
        User viewer = user(VIEWER_PHONE, "Dev Viewer");
        User norole = user(NOROLE_PHONE, "Dev Norole");
        User workerB = user(WORKER_B_PHONE, "Dev Worker B");

        // OWNER ndiyo role pekee iliyopandwa yenye manage_users - ndiyo
        // "admin" anayehitajika hapa. Bado ni role ya kawaida: ikihaririwa
        // kwenye majaribio, ruhusa zake zinabadilika, na hiyo ni sahihi.
        membership(admin, farmA, role("OWNER"));
        // farms.owner_user_id, si role: ndicho kikwazo pekee cha kweli
        // kwenye uanachama (FarmUserService.removeMembership - mmiliki
        // hawezi kutolewa kwenye shamba lake). Bila mmiliki, kikwazo hicho
        // hakiwezi kufikiwa hata kwa majaribio.
        owner(farmA, admin);
        membership(worker, farmA, role("WORKER"));
        membership(viewer, farmA, role("VIEWER"));
        membership(norole, farmA, null);
        membership(workerB, farmB, role("WORKER"));

        ProductionUnit unitA = unit(farmA, "DEV-A1");
        unit(farmB, "DEV-B1");
        cycle(unitA);

        logger.info("Wahusika wa dev tayari (password: {}): {}=OWNER, {}=WORKER, {}=VIEWER, "
                        + "{}=hana role, {}=WORKER wa shamba jingine. Mashamba: '{}' (#{}) na '{}' (#{}).",
                DEV_PASSWORD, ADMIN_PHONE, WORKER_PHONE, VIEWER_PHONE, NOROLE_PHONE, WORKER_B_PHONE,
                FARM_A, farmA.getFarmId(), FARM_B, farmB.getFarmId());
    }

    /**
     * farms.name ni UNIQUE tangu V9, hivyo jina ndilo kitambulisho salama
     * cha idempotency. FarmRepository haina findByName na haiongezwi hapa:
     * hii ni njia ya dev pekee, na mashamba ni machache.
     */
    private Farm farmByName(String name, String location) {
        Optional<Farm> existing = farmRepository.findAll().stream()
                .filter(f -> name.equals(f.getName()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Farm farm = new Farm();
        farm.setName(name);
        farm.setLocation(location);
        return farmRepository.save(farm);
    }

    private User user(String phone, String name) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setName(name);
            user.setPhone(phone);
            user.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            user.setStatus(UserStatus.ACTIVE);
            user.setIsRoot(false);
            // Tofauti na ROOT: hakuna lango la kubadilisha password, la
            // sivyo kila curl ya majaribio ingekwama kwenye 403.
            user.setMustChangePassword(false);
            return userRepository.save(user);
        });
    }

    private void membership(User user, Farm farm, Role role) {
        if (farmUserRepository.existsByUser_UserIdAndFarm_FarmId(user.getUserId(), farm.getFarmId())) {
            return;
        }
        FarmUser membership = new FarmUser();
        membership.setUser(user);
        membership.setFarm(farm);
        membership.setRole(role);
        farmUserRepository.save(membership);
    }

    /** Create-if-not-set: mmiliki aliyewekwa kwa mkono kwenye majaribio haguswi. */
    private void owner(Farm farm, User user) {
        if (farm.getOwner() == null) {
            farm.setOwner(user);
            farmRepository.save(farm);
        }
    }

    private Role role(String name) {
        return roleRepository.findByName(name).orElse(null);
    }

    private ProductionUnit unit(Farm farm, String code) {
        Optional<ProductionUnit> existing = unitRepository.findByFarm_FarmId(farm.getFarmId()).stream()
                .filter(u -> code.equals(u.getCode()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        ProductionUnit unit = new ProductionUnit();
        unit.setFarm(farm);
        unit.setCode(code);
        unit.setType(ProductionUnit.UnitType.TANK);
        unit.setSizeM3(new BigDecimal("20.0"));
        unit.setWaterSource("Kisima");
        unit.setStatus("ACTIVE");
        return unitRepository.save(unit);
    }

    /**
     * Mzunguko mmoja kwenye shamba A, ili query za kufuata mzunguko ziwe na
     * kitu cha kuulizia.
     *
     * Unaandikwa moja kwa moja badala ya kupitia CycleService kwa makusudi:
     * service inadai muktadha wa mtumiaji aliyeingia (PermissionChecker) -
     * ambaye hayupo wakati wa startup - na inazalisha daily_tasks ambazo si
     * sehemu ya lengo hapa.
     */
    private void cycle(ProductionUnit unit) {
        List<Cycle> existing = cycleRepository.findByUnit_Farm_FarmId(unit.getFarm().getFarmId());
        if (!existing.isEmpty()) {
            return;
        }
        Species species = speciesRepository.findAll().stream().findFirst().orElse(null);
        if (species == null) {
            logger.warn("Hakuna species yoyote - mzunguko wa dev haujatengenezwa.");
            return;
        }
        Cycle cycle = new Cycle();
        cycle.setUnit(unit);
        cycle.setSpecies(species);
        cycle.setStockingDate(LocalDate.now().minusMonths(1));
        cycle.setFingerlingsCount(500);
        cycle.setStatus("ACTIVE");
        cycleRepository.save(cycle);
    }
}
