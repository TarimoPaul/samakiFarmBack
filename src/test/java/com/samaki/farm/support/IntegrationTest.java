package com.samaki.farm.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.dev.DevSeedService;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.productionunit.entity.ProductionUnit;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.rbac.services.RbacSeedService;
import com.samaki.farm.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Msingi wa majaribio yote ya integration: app halisi, HTTP halisi,
 * database halisi.
 *
 * =====================================================================
 * KUTENGANISHA TESTS - KWA NINI SI @Transactional ROLLBACK
 *
 * Njia ya kawaida ya Spring (test inafungua transaction, inairudisha nyuma
 * mwishoni) HAIFANYI KAZI hapa, na ni hatari kwa sababu inaonekana kana
 * kwamba inafanya kazi: haya ni majaribio ya HTTP kamili, hivyo SERVER
 * ndiye anayefungua na kufunga transaction yake kwenye thread nyingine
 * kabisa. Transaction ya test haigusi kitu alichoandika server - kila
 * kitu kinabaki kwenye database, na "isolation" ingekuwa ya kufikirika.
 *
 * Badala yake: SCHEMA INAJENGWA UPYA kabla ya KILA test - Flyway clean +
 * migrate, kisha fixture inapandwa upya. Kila test inaanza kwenye hali
 * ile ile hasa, haijalishi iliyopita iliandika nini au ilianguka wapi.
 *
 * TRUNCATE ilijaribiwa kwanza na IKAKATALIWA: angalia
 * resetAndReseedSchema() kwa mtego uliofichika ambao V2 iliuweka.
 *
 * Hii pia inarudisha RBAC kwa hali ya CSV kila mara, jambo ambalo test
 * ya D-13 (inayohariri ruhusa za role wakati wa run) inalihitaji ili
 * isiwaachie wenzake RBAC iliyobadilika.
 *
 * Cache za static za JwtAuthFilter zinafutwa pia: zinashikilia principal
 * kwa UUID, na baada ya kujenga upya UUID zote ni mpya.
 * =====================================================================
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// `dev` inaleta DevSeedService (fixture); `test` ni ya application-test.yml.
@ActiveProfiles({"dev", "test"})
public abstract class IntegrationTest {

    /** Password ya wahusika wote wa dev - angalia DevSeedService. */
    protected static final String PASSWORD = DevSeedService.DEV_PASSWORD;

    protected static final String ADMIN_PHONE = "0700100001";
    protected static final String WORKER_PHONE = "0700100002";
    protected static final String VIEWER_PHONE = "0700100003";
    protected static final String NOROLE_PHONE = "0700100004";
    protected static final String WORKER_B_PHONE = "0700100005";

    /**
     * Inatengeneza database ya majaribio KABLA context haijaanza, kisha
     * inaielekeza Spring hapo. Flyway inaendesha V1 -> ya mwisho juu ya
     * database tupu kabisa - ndipo migrations zinapojaribiwa.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        TestDatabase.createOnce();
        registry.add("spring.datasource.url", TestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    @Autowired protected TestRestTemplate rest;
    @Autowired protected ObjectMapper json;
    @Autowired private Flyway flyway;
    @Autowired private RbacSeedService rbacSeedService;
    @Autowired private DevSeedService devSeedService;
    @Autowired private FarmRepository farmRepository;
    @Autowired private ProductionUnitRepository unitRepository;
    @Autowired private CycleRepository cycleRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;

    // Fixture ya sasa. Vitambulisho VINASOMWA, havijaandikwa kwa mkono:
    // schema inajengwa upya kila test, hivyo namba hazifanani na zile za
    // database ya dev (na hazipaswi kutegemewa kuwa 26/27).
    protected int farmA;
    protected int farmB;
    protected int unitA;
    protected int unitB;
    protected int cycleA;
    protected UUID adminId;
    protected UUID workerId;

    protected String adminToken;
    protected String workerToken;
    protected String viewerToken;
    protected String noroleToken;
    protected String workerBToken;

    @BeforeEach
    void resetAndSeed() {
        resetAndReseedSchema();

        Farm a = farmByName("Dev Farm A");
        Farm b = farmByName("Dev Farm B");
        farmA = a.getFarmId();
        farmB = b.getFarmId();
        unitA = unitRepository.findByFarm_FarmId(farmA).get(0).getUnitId();
        unitB = unitRepository.findByFarm_FarmId(farmB).get(0).getUnitId();
        cycleA = cycleRepository.findByUnit_Farm_FarmId(farmA).stream()
                .map(Cycle::getCycleId).findFirst().orElseThrow();
        adminId = userRepository.findByPhone(ADMIN_PHONE).orElseThrow().getUserId();
        workerId = userRepository.findByPhone(WORKER_PHONE).orElseThrow().getUserId();

        adminToken = login(ADMIN_PHONE);
        workerToken = login(WORKER_PHONE);
        viewerToken = login(VIEWER_PHONE);
        noroleToken = login(NOROLE_PHONE);
        workerBToken = login(WORKER_B_PHONE);
    }

    /**
     * Flyway clean + migrate, kisha kupanda fixture upya.
     *
     * KWA NINI SI `TRUNCATE ... CASCADE`, ambayo ndiyo njia ya kawaida:
     * V2 iliongeza `updated_by`/`deleted_by` (FK -> users) kwenye KILA
     * jedwali, hivyo `TRUNCATE users CASCADE` inasomba schema NZIMA -
     * ikiwemo katalogi inayotoka kwenye migrations (species, roles,
     * permissions). Jaribio la kwanza la harness hii lilifanya hivyo:
     * seeder iliendelea kuandika "Wahusika wa dev tayari" huku
     * `role("OWNER")` ikirudisha null kimyakimya, hivyo kila mwanachama
     * alikuwa hana role - fixture iliyoharibika ikijifanya nzima.
     *
     * Kusafisha kwa Flyway kunaepuka mtego huo kabisa: hali ya kuanzia ni
     * ILE ILE database mpya inayopata, ikiwa na data ya V1 (roles,
     * species) bila kuiandika tena hapa - kunakili data ya seed kwenye
     * test ni kualika drift.
     *
     * Faida ya pili: migrations zinaendeshwa kutoka utupu kwa KILA test,
     * si mara moja - ndicho kinachozifanya kuwa kitu kinachojaribiwa
     * kikweli.
     */
    private void resetAndReseedSchema() {
        guardTestDatabase();
        flyway.clean();
        flyway.migrate();

        // permissions + role_permissions kutoka CSV; roles/species zimetoka
        // kwenye migrations tayari. (ROOT hatengenezwi: ROOT_PHONE/
        // ROOT_PASSWORD hazipo kwenye majaribio - onyo tu kwenye log.)
        rbacSeedService.seedAll();
        devSeedService.seed();

        // Principal wa zamani wamehifadhiwa kwa UUID ambazo sasa hazipo.
        JwtAuthFilter.clearAllUserCache();
        JwtAuthFilter.clearRootCache();
    }

    /**
     * flyway.clean() inafuta KILA kitu. Kabla ya kuiita, thibitisha
     * kwamba tuko kwenye database ya majaribio inayotupwa - si dev, wala
     * kitu kingine chochote. Gharama ni query moja; inayozuiwa ni
     * kufuta database ya kweli.
     */
    private void guardTestDatabase() {
        String current = inTx(() -> (String) entityManager
                .createNativeQuery("SELECT current_database()").getSingleResult());
        if (!current.startsWith("samaki_test_") || !current.equals(TestDatabase.databaseName())) {
            throw new IllegalStateException(
                    "KUSIMAMISHWA: majaribio yameunganishwa na database isiyo ya majaribio: " + current);
        }
    }

    /**
     * Inaendesha kazi ndani ya transaction YAKE.
     *
     * Tests HAZIPASWI kuwa na @Transactional: Spring inafungua transaction
     * ya test KABLA ya @BeforeEach, hivyo `readOnly = true` ingefanya
     * TRUNCATE ya kupanda upya ishindwe - na test ingeonekana kama ina
     * hitilafu ya database ilhali tatizo ni mpangilio wa annotation.
     */
    protected <T> T inTx(java.util.function.Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    protected Farm farmByName(String name) {
        return farmRepository.findAll().stream()
                .filter(f -> name.equals(f.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Shamba halipo kwenye fixture: " + name));
    }

    protected ProductionUnit unit(int unitId) {
        return unitRepository.findById(unitId).orElseThrow();
    }

    // ------------------------------------------------------------- HTTP

    protected String login(String phone) {
        ResponseEntity<String> response = post("/api/auth/login",
                "{\"phone\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\"}", null);
        JsonNode body = parse(response);
        JsonNode token = body.path("data").path("token");
        if (token.isMissingNode() || token.isNull()) {
            throw new IllegalStateException("Login imeshindwa kwa " + phone + ": " + response.getBody());
        }
        return token.asText();
    }

    protected ResponseEntity<String> get(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
    }

    protected ResponseEntity<String> post(String path, String body, String token) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    protected ResponseEntity<String> put(String path, String body, String token) {
        return exchange(HttpMethod.PUT, path, body, token);
    }

    protected ResponseEntity<String> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, null, token);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    /** Ombi la GraphQL. Kumbuka: hitilafu za resolver zinarudi HTTP 200. */
    protected JsonNode graphql(String token, String query) {
        String body;
        try {
            body = json.writeValueAsString(java.util.Map.of("query", query));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return parse(post("/graphql", body, token));
    }

    /** errorCode ya kwanza kwenye jibu la GraphQL, au null. */
    protected String graphqlErrorCode(JsonNode response) {
        JsonNode errors = response.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return null;
        }
        JsonNode code = errors.get(0).path("extensions").path("errorCode");
        return code.isMissingNode() || code.isNull() ? null : code.asText();
    }

    protected String graphqlMessage(JsonNode response) {
        JsonNode errors = response.path("errors");
        return errors.isArray() && !errors.isEmpty() ? errors.get(0).path("message").asText() : null;
    }

    protected JsonNode parse(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Jibu si JSON: " + response.getBody(), e);
        }
    }
}
