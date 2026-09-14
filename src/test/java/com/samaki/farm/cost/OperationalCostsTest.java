package com.samaki.farm.cost;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.cost.entity.Cost;
import com.samaki.farm.cost.entity.CostCategory;
import com.samaki.farm.cost.repository.CostCategoryRepository;
import com.samaki.farm.cost.repository.CostRepository;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.rbac.repository.RoleRepository;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DAFTARI LA GHARAMA ZA UENDESHAJI - V23/V24.
 *
 * =====================================================================
 * KINACHOJARIBIWA HAPA NA HAKIJARIBIWI KWENYE MALI
 *
 * Nusu ya majaribio haya ni ya AssetRegisterTest kwa umbo jipya: daftari
 * la kampuni linavuka mashamba ya mwombaji, halivuji shamba asilo lake,
 * na katalogi yake ni ya kimfumo. Yale hayana budi kuwepo tena - ni
 * module tofauti yenye service yake, na sheria isiyojaribiwa ni sheria
 * inayoweza kutoweka.
 *
 * NUSU NYINGINE HAINA MFANO KWENYE MALI, na ndiyo safu ya MZUNGUKO:
 *
 *   * cycleId = null ni gharama ya SHAMBA ZIMA - HALI HALALI, si data
 *     iliyokosekana. Ndiyo hali ya umeme, mishahara na kodi.
 *   * cycleId iliyowekwa LAZIMA iwe ya shamba lililochaguliwa. Hii ndiyo
 *     sheria pekee ambayo database HAIWEZI kuisema yenyewe: `cycles`
 *     haina farm_id, hivyo FK ya V23 ingekubali mzunguko wa shamba
 *     lolote. Jaribio linalothibitisha hilo linamweka mwombaji kuwa
 *     mwanachama wa MASHAMBA YOTE MAWILI kwa makusudi - vinginevyo
 *     lingekuwa likipima ruhusa (FORBIDDEN ya shamba), si mechi ya
 *     mzunguko na shamba.
 *   * Mzunguko ULIOFUNGWA unakubalika, na kichagua kinamwonyesha. Bili
 *     ya Machi inalipwa Aprili, mara nyingi baada ya mavuno.
 * =====================================================================
 */
@DisplayName("Daftari la gharama za uendeshaji")
class OperationalCostsTest extends IntegrationTest {

    private static final String COSTS_QUERY = """
            query { costs { costId amount costDate description \
            farm { farmId name } cycle { cycleId label status } \
            costCategory { costCategoryId name } } }""";

    private static final String CATEGORIES_QUERY =
            "query { costCategories { costCategoryId name } }";

    /** Kanda ile ile CostService inayoitumia kufafanua "leo". */
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    @Autowired private CostRepository costRepository;
    @Autowired private CostCategoryRepository costCategoryRepository;
    @Autowired private CycleRepository cycles;
    @Autowired private ProductionUnitRepository units;
    @Autowired private SpeciesRepository species;
    @Autowired private FarmRepository farms;
    @Autowired private FarmUserRepository farmUsers;
    @Autowired private RoleRepository roles;

    // --------------------------------------------------------- misaada

    private int category(String name) {
        JsonNode res = graphql(adminToken,
                "mutation { createCostCategory(name: \"" + name + "\") { costCategoryId name } }");
        return res.path("data").path("createCostCategory").path("costCategoryId").asInt();
    }

    /** cycleId null = gharama ya shamba zima (hoja haitumwi kabisa). */
    private String createMutation(int farmId, Integer cycleId, int categoryId,
                                   String amount, String costDate, String description) {
        return "mutation { createCost(farmId: " + farmId
                + (cycleId == null ? "" : ", cycleId: " + cycleId)
                + ", costCategoryId: " + categoryId
                + ", amount: " + amount
                + ", costDate: \"" + costDate + "\""
                + (description == null ? "" : ", description: \"" + description + "\"")
                + ") { costId amount costDate description farm { farmId name } "
                + "cycle { cycleId label status } costCategory { costCategoryId name } } }";
    }

    private JsonNode register(String token) {
        return graphql(token, COSTS_QUERY).path("data").path("costs");
    }

    private JsonNode rowWithAmount(String token, double amount) {
        for (JsonNode row : register(token)) {
            if (row.path("amount").asDouble() == amount) {
                return row;
            }
        }
        return null;
    }

    private String today() {
        return LocalDate.now(EAT).toString();
    }

    /**
     * Uanachama wa PILI kwa admin - "mmiliki wa mashamba mengi" ambaye
     * madaftari ya kampuni yamejengwa kwa ajili yake, na ambaye fixture
     * ya dev haina (kila mtu ana shamba moja).
     *
     * Cache ya JwtAuthFilter inafutwa: inashikilia principal kwa dakika
     * 15, hivyo ombi linalofuata lingesoma uanachama wa zamani.
     */
    private void addAdminToFarmB() {
        inTx(() -> {
            FarmUser membership = new FarmUser();
            membership.setUser(userRepository.findByUserId(adminId).orElseThrow());
            membership.setFarm(farms.findByFarmId(farmB).orElseThrow());
            membership.setRole(roles.findByName("OWNER").orElseThrow());
            return farmUsers.save(membership);
        });
        JwtAuthFilter.clearUserCache(adminId);
    }

    /**
     * Mzunguko kwenye shamba B - fixture ya dev ina tanki la B lakini
     * HAKUNA mzunguko ndani yake (angalia DevSeedService), na jaribio la
     * mzunguko-wa-shamba-jingine linaudai.
     */
    private int cycleInFarmB() {
        return inTx(() -> {
            Cycle cycle = new Cycle();
            cycle.setUnit(units.findByFarm_FarmId(farmB).get(0));
            cycle.setSpecies(species.findAll().get(0));
            cycle.setStockingDate(LocalDate.now(EAT).minusMonths(2));
            cycle.setFingerlingsCount(300);
            cycle.setStatus(Cycle.ACTIVE);
            return cycles.save(cycle).getCycleId();
        });
    }

    /**
     * Kufunga mzunguko MOJA KWA MOJA kwenye database.
     *
     * closeCycle ingedai mavuno kamili na ruhusa ya `edit_cycle`; hali
     * inayojaribiwa hapa ni "status si ACTIVE", si njia iliyoifikisha.
     */
    private void closeCycleDirectly(int cycleId) {
        inTx(() -> {
            Cycle cycle = cycles.findByCycleId(cycleId).orElseThrow();
            cycle.setStatus(Cycle.HARVESTED);
            cycle.setActualHarvestDate(LocalDate.now(EAT).minusDays(3));
            return cycles.save(cycle);
        });
    }

    /**
     * Gharama inayoandikwa MOJA KWA MOJA kwenye database.
     *
     * Inahitajika kwa jaribio la kuvuja: ili kuonyesha kwamba shamba
     * ambalo mwombaji si mwanachama wake HALIONEKANI, ni lazima liwe na
     * gharama - na API yenyewe ingekataa kuiandika hapo, ambayo ndiyo
     * sheria inayojaribiwa.
     */
    private void writeCostDirectly(int farmId, int categoryId, String amount) {
        inTx(() -> {
            Cost cost = new Cost();
            cost.setFarm(farms.findByFarmId(farmId).orElseThrow());
            cost.setCostCategory(costCategoryRepository.findByCostCategoryId(categoryId).orElseThrow());
            cost.setAmount(new BigDecimal(amount));
            cost.setCostDate(LocalDate.now(EAT).minusDays(1));
            return costRepository.save(cost);
        });
    }

    // =====================================================================

    @Nested
    @DisplayName("kurekodi")
    class Kurekodi {

        @Test
        @DisplayName("gharama ya MZUNGUKO inahifadhiwa ikiwa na shamba, mzunguko NA aina")
        void persistsWithFarmCycleAndCategory() {
            int dawa = category("Dawa");

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, cycleA, dawa, "45000", "2024-03-15", "Dawa ya bwawa"));

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode created = res.path("data").path("createCost");
            assertThat(created.path("costId").asInt()).isPositive();
            assertThat(created.path("amount").asDouble()).isEqualTo(45000.0);
            assertThat(created.path("costDate").asText()).isEqualTo("2024-03-15");
            assertThat(created.path("description").asText()).isEqualTo("Dawa ya bwawa");
            assertThat(created.path("farm").path("farmId").asInt()).isEqualTo(farmA);
            assertThat(created.path("farm").path("name").asText()).isEqualTo("Dev Farm A");
            assertThat(created.path("cycle").path("cycleId").asInt()).isEqualTo(cycleA);
            assertThat(created.path("cycle").path("status").asText()).isEqualTo("ACTIVE");
            // Lebo inajengwa backend: tanki + aina + hali.
            assertThat(created.path("cycle").path("label").asText())
                    .contains("DEV-A1").contains("(ACTIVE)");
            assertThat(created.path("costCategory").path("name").asText()).isEqualTo("Dawa");

            // Na si kwenye jibu la mutation pekee - kwenye DATABASE.
            Cost saved = inTx(() -> costRepository.findAll().get(0));
            assertThat(saved.getFarm().getFarmId()).isEqualTo(farmA);
            assertThat(saved.getCycle().getCycleId()).isEqualTo(cycleA);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("45000.00"));
        }

        /**
         * NUSU YA MODULE. Umeme wa mwezi hauendeshi mzunguko mmoja -
         * unaendesha shamba. Kukosekana kwa mzunguko si data
         * iliyokosekana; ni aina nyingine ya gharama.
         */
        @Test
        @DisplayName("gharama ya SHAMBA ZIMA inahifadhiwa BILA mzunguko")
        void wholeFarmCostHasNoCycle() {
            int umeme = category("Umeme");

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, null, umeme, "120000", "2024-04-02", "LUKU ya Machi"));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("createCost").path("cycle").isNull()).isTrue();

            // Kwenye daftari pia - si kwenye jibu la mutation pekee.
            JsonNode row = rowWithAmount(adminToken, 120000.0);
            assertThat(row).isNotNull();
            assertThat(row.path("cycle").isNull()).isTrue();
            assertThat(row.path("farm").path("farmId").asInt()).isEqualTo(farmA);

            assertThat(inTx(() -> costRepository.findAll().get(0).getCycle())).isNull();
        }

        @Test
        @DisplayName("maelezo yenye nafasi tupu pekee yanakuwa null, si maandishi matupu")
        void blankDescriptionBecomesNull() {
            int mafuta = category("Mafuta");

            graphql(adminToken, createMutation(farmA, null, mafuta, "30000", "2024-05-01", "   "));

            assertThat(rowWithAmount(adminToken, 30000.0).path("description").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("daftari la kampuni")
    class Kampuni {

        @Test
        @DisplayName("mmiliki wa mashamba mawili anaona gharama za YOTE kwenye orodha moja")
        void spansEveryFarmTheCallerBelongsTo() {
            int umeme = category("Umeme");
            addAdminToFarmB();

            graphql(adminToken, createMutation(farmA, null, umeme, "100000", "2024-02-01", null));
            graphql(adminToken, createMutation(farmB, null, umeme, "200000", "2024-04-01", null));

            JsonNode register = register(adminToken);

            assertThat(register).hasSize(2);
            assertThat(rowWithAmount(adminToken, 100000.0).path("farm").path("name").asText())
                    .isEqualTo("Dev Farm A");
            assertThat(rowWithAmount(adminToken, 200000.0).path("farm").path("name").asText())
                    .isEqualTo("Dev Farm B");
        }

        /**
         * Upande wa pili wa sarafu ile ile: bila jaribio hili, "orodha
         * inayovuka mashamba" ingeweza kuwa imetekelezwa kama "mashamba
         * YOTE ya kampuni".
         */
        @Test
        @DisplayName("shamba ambalo mwombaji si mwanachama wake HALIVUJI kwenye daftari lake")
        void doesNotLeakFarmsTheCallerHasNoMembershipIn() {
            int umeme = category("Umeme");
            // Admin ni mwanachama wa A pekee (fixture ya dev).
            writeCostDirectly(farmB, umeme, "500000");
            graphql(adminToken, createMutation(farmA, null, umeme, "80000", "2024-05-05", null));

            JsonNode register = register(adminToken);

            assertThat(register).hasSize(1);
            assertThat(register.get(0).path("amount").asDouble()).isEqualTo(80000.0);
            assertThat(rowWithAmount(adminToken, 500000.0)).isNull();
        }

        @Test
        @DisplayName("kurekodi kwenye shamba asilo lake ni FORBIDDEN, si rekodi iliyoandikwa")
        void cannotRecordIntoAnotherFarm() {
            int umeme = category("Umeme");

            JsonNode res = graphql(adminToken,
                    createMutation(farmB, null, umeme, "90000", "2024-05-05", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        @Test
        @DisplayName("orodha tupu si hitilafu")
        void emptyRegisterIsNotAnError() {
            JsonNode res = graphql(adminToken, COSTS_QUERY);

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("costs")).isEmpty();
        }
    }

    @Nested
    @DisplayName("mzunguko")
    class Mzunguko {

        /**
         * SHERIA AMBAYO DATABASE HAIWEZI KUISEMA. `cycles` haina farm_id
         * (shamba lake linafikiwa kupitia production_units), hivyo FK ya
         * V23 ingekubali mzunguko wa shamba lolote kwenye gharama ya
         * shamba lolote.
         *
         * Admin anawekwa kuwa mwanachama wa MASHAMBA YOTE MAWILI kwa
         * makusudi: bila hivyo jaribio lingesimama kwenye FORBIDDEN ya
         * shamba na lisingeigusa sheria hii hata kidogo.
         */
        @Test
        @DisplayName("mzunguko wa shamba JINGINE unakataliwa hata kwa mwanachama wa yote mawili")
        void rejectsCycleFromAnotherFarm() {
            int dawa = category("Dawa");
            addAdminToFarmB();
            int cycleB = cycleInFarmB();

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, cycleB, dawa, "50000", "2024-05-05", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("haupo kwenye shamba ulilochagua");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        /**
         * JIBU LILE LILE la mzunguko usiopo: kutofautisha "haupo" na "si
         * wa shamba hili" kungemwambia mwombaji ni mizunguko mingapi ipo
         * kwenye mashamba asiyoyajua.
         */
        @Test
        @DisplayName("mzunguko usiojulikana unakataliwa kwa jibu lile lile")
        void rejectsUnknownCycle() {
            int dawa = category("Dawa");

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, 99999, dawa, "50000", "2024-05-05", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("haupo kwenye shamba ulilochagua");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        /**
         * Bili ya Machi inalipwa Aprili - mara nyingi baada ya mavuno.
         * Mzunguko uliofungwa ukikataliwa, gharama hiyo ingelazimika
         * kuwa ya shamba zima, na ripoti ya faida ya mzunguko
         * ingeikosa.
         */
        @Test
        @DisplayName("mzunguko ULIOFUNGWA unakubalika - gharama zinafika baada ya mavuno")
        void acceptsClosedCycle() {
            int dawa = category("Dawa");
            closeCycleDirectly(cycleA);

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, cycleA, dawa, "60000", "2024-05-05", null));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("createCost").path("cycle").path("status").asText())
                    .isEqualTo("HARVESTED");
        }

        @Test
        @DisplayName("kichagua kinaonyesha mizunguko YOTE ya shamba - inayoendelea na iliyofungwa")
        void farmCyclesListsActiveAndClosed() {
            JsonNode active = graphql(adminToken,
                    "query { farmCycles(farmId: " + farmA + ") { cycleId label status } }");
            assertThat(graphqlErrorCode(active)).isNull();
            assertThat(active.path("data").path("farmCycles")).hasSize(1);

            closeCycleDirectly(cycleA);

            JsonNode after = graphql(adminToken,
                    "query { farmCycles(farmId: " + farmA + ") { cycleId label status } }");
            JsonNode row = after.path("data").path("farmCycles").get(0);

            // BADO IPO baada ya kufungwa - hilo ndilo jaribio.
            assertThat(after.path("data").path("farmCycles")).hasSize(1);
            assertThat(row.path("cycleId").asInt()).isEqualTo(cycleA);
            assertThat(row.path("status").asText()).isEqualTo("HARVESTED");
            assertThat(row.path("label").asText()).contains("DEV-A1").contains("(HARVESTED)");
        }

        @Test
        @DisplayName("kichagua cha shamba asilo lake ni FORBIDDEN")
        void farmCyclesRejectsForeignFarm() {
            JsonNode res = graphql(adminToken,
                    "query { farmCycles(farmId: " + farmB + ") { cycleId label } }");

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
        }
    }

    @Nested
    @DisplayName("katalogi ya aina")
    class Katalogi {

        @Test
        @DisplayName("aina mpya inaingia kwenye katalogi na inasomeka mara moja")
        void createsAndLists() {
            JsonNode res = graphql(adminToken,
                    "mutation { createCostCategory(name: \"Usafiri\") { costCategoryId name } }");

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("createCostCategory").path("name").asText())
                    .isEqualTo("Usafiri");

            JsonNode katalogi = graphql(adminToken, CATEGORIES_QUERY).path("data").path("costCategories");
            assertThat(katalogi).hasSize(1);
            assertThat(katalogi.get(0).path("name").asText()).isEqualTo("Usafiri");
        }

        @Test
        @DisplayName("katalogi inaanza TUPU - hakuna orodha ya kudumu iliyopandwa")
        void startsEmpty() {
            assertThat(graphql(adminToken, CATEGORIES_QUERY).path("data").path("costCategories"))
                    .isEmpty();
        }

        @Test
        @DisplayName("nafasi tupu pembeni mwa jina zinaondolewa")
        void trimsName() {
            graphql(adminToken, "mutation { createCostCategory(name: \"  Mishahara  \") { name } }");

            JsonNode katalogi = graphql(adminToken, CATEGORIES_QUERY).path("data").path("costCategories");
            assertThat(katalogi.get(0).path("name").asText()).isEqualTo("Mishahara");
        }

        @Test
        @DisplayName("jina tupu linakataliwa")
        void refusesBlankName() {
            assertThat(graphqlErrorCode(graphql(adminToken,
                    "mutation { createCostCategory(name: \"   \") { name } }")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("jina lililochukuliwa linakataliwa kwa CONFLICT, si kwa hitilafu ya database")
        void refusesDuplicateName() {
            category("Umeme");

            JsonNode res = graphql(adminToken,
                    "mutation { createCostCategory(name: \"Umeme\") { costCategoryId } }");

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            assertThat(graphqlMessage(res)).contains("jina hili tayari ipo");
        }

        /**
         * `cost_categories.name` ni UNIQUE ya kawaida (V23), lakini
         * @SQLRestriction inaificha aina iliyofutwa kwenye kila query ya
         * JPA. Bila swali la native, ukaguzi wetu ungepita na database
         * ndiyo ingekataa - CONFLICT yenye sentensi isiyomweleza
         * msimamizi kwamba tatizo ni jina ASILOLIONA. Sheria ile ile ya
         * AssetCategory, FeedType na Species.
         */
        @Test
        @DisplayName("jina la aina ILIYOFUTWA bado limechukuliwa")
        void refusesNameOfSoftDeletedCategory() {
            int id = category("Zamani");
            inTx(() -> {
                CostCategory category = costCategoryRepository.findByCostCategoryId(id).orElseThrow();
                category.softDelete(adminId);
                return costCategoryRepository.save(category);
            });
            assertThat(graphql(adminToken, CATEGORIES_QUERY).path("data").path("costCategories"))
                    .isEmpty();

            JsonNode res = graphql(adminToken,
                    "mutation { createCostCategory(name: \"Zamani\") { costCategoryId } }");

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            assertThat(graphqlMessage(res)).contains("jina hili tayari ipo");
        }
    }

    @Nested
    @DisplayName("uthibitisho")
    class Uthibitisho {

        @Test
        @DisplayName("kiasi cha sifuri au hasi kinakataliwa")
        void refusesNonPositiveAmount() {
            int umeme = category("Umeme");

            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation(farmA, null, umeme, "0", "2024-01-01", null))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation(farmA, null, umeme, "-5000", "2024-01-01", null))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        /**
         * 0.004 ni CHANYA, lakini kwa NUMERIC(14,2) ni 0.00 - ambayo
         * CHECK ya V23 ingeikataa kama ukiukwaji wa kikwazo cha
         * database.
         */
        @Test
        @DisplayName("kiasi kinachoshuka hadi sifuri baada ya kuzungushwa kinakataliwa kwa ujumbe")
        void refusesAmountThatRoundsToZero() {
            int umeme = category("Umeme");

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, null, umeme, "0.004", "2024-01-01", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        /**
         * Gharama ambayo bado haijatokea si gharama - ni bajeti, na
         * daftari la bajeti si daftari la matumizi. Sheria ile ile ya
         * AssetService na DailyTaskService, na "leo" ni ya EAT kwa
         * sababu ile ile.
         */
        @Test
        @DisplayName("tarehe ya baadaye inakataliwa")
        void refusesFutureCostDate() {
            int umeme = category("Umeme");
            String kesho = LocalDate.now(EAT).plusDays(1).toString();

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, null, umeme, "10000", kesho, null));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("ya baadaye");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        @Test
        @DisplayName("mbali zaidi ya kesho pia - si suala la siku moja")
        void refusesFarFutureToo() {
            int umeme = category("Umeme");
            String mwakaUjao = LocalDate.now(EAT).plusYears(1).toString();

            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation(farmA, null, umeme, "10000", mwakaUjao, null))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        /**
         * LEO na ZAMANI zote ni halali, na ya zamani ndiyo hali ya
         * kawaida: bili ya Machi inaingizwa Aprili.
         */
        @Test
        @DisplayName("tarehe ya leo na ya nyuma zinakubaliwa")
        void acceptsTodayAndPast() {
            int umeme = category("Umeme");

            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation(farmA, null, umeme, "10000", today(), null)))).isNull();
            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation(farmA, null, umeme, "10000", "2019-07-04", null)))).isNull();
        }

        @Test
        @DisplayName("tarehe isiyosomeka inakataliwa kwa ujumbe unaotaja muundo")
        void refusesUnparseableDate() {
            int umeme = category("Umeme");

            JsonNode res = graphql(adminToken,
                    createMutation(farmA, null, umeme, "10000", "15/03/2024", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("YYYY-MM-DD");
        }

        @Test
        @DisplayName("aina isiyojulikana inakataliwa")
        void refusesUnknownCategory() {
            JsonNode res = graphql(adminToken,
                    createMutation(farmA, null, 99999, "10000", "2024-01-01", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }
    }

    @Nested
    @DisplayName("ruhusa")
    class Ruhusa {

        @Test
        @DisplayName("WORKER hawezi kurekodi gharama")
        void workerCannotRecord() {
            int umeme = category("Umeme");

            JsonNode res = graphql(workerToken,
                    createMutation(farmA, null, umeme, "10000", "2024-01-01", null));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(inTx(() -> costRepository.count())).isZero();
        }

        /**
         * Kusoma nako ni `manage_costs`, kama daftari la mali: bili ya
         * umeme na mishahara ni matumizi ya kampuni, si taarifa ya kazi
         * ya leo.
         */
        @Test
        @DisplayName("WORKER hawezi hata KUSOMA daftari wala kichagua cha mizunguko")
        void workerCannotRead() {
            assertThat(graphqlErrorCode(graphql(workerToken, COSTS_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, CATEGORIES_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken,
                    "query { farmCycles(farmId: " + farmA + ") { cycleId } }")))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("VIEWER hawezi kusoma wala kuandika")
        void viewerCannotReadOrWrite() {
            int umeme = category("Umeme");

            assertThat(graphqlErrorCode(graphql(viewerToken, COSTS_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken, CATEGORIES_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken,
                    createMutation(farmA, null, umeme, "10000", "2024-01-01", null))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken,
                    "mutation { createCostCategory(name: \"Ya mtazamaji\") { costCategoryId } }")))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("asiye na role hana ruhusa - lango ni ruhusa, si uanachama")
        void noRoleIsForbidden() {
            assertThat(graphqlErrorCode(graphql(noroleToken, COSTS_QUERY))).isEqualTo("FORBIDDEN");
        }

        /**
         * FARM_MANAGER ANAYO (V24) - ndiye anayeendesha shamba kila
         * siku, hivyo ndiye anayejua bili ilipofika. Fixture ya dev
         * haina mtu wa nafasi hiyo, hivyo uthibitisho unafanywa kwa
         * kubadilisha role ya mfanyakazi.
         */
        @Test
        @DisplayName("FARM_MANAGER anayo ruhusa - si OWNER pekee")
        void farmManagerIsAllowed() {
            inTx(() -> {
                FarmUser membership = farmUsers
                        .findByUser_UserIdAndFarm_FarmId(workerId, farmA).orElseThrow();
                membership.setRole(roles.findByName("FARM_MANAGER").orElseThrow());
                return farmUsers.save(membership);
            });
            JwtAuthFilter.clearUserCache(workerId);

            JsonNode res = graphql(workerToken,
                    "mutation { createCostCategory(name: \"Ya meneja\") { costCategoryId name } }");

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(graphqlErrorCode(graphql(workerToken, COSTS_QUERY))).isNull();
        }
    }
}
