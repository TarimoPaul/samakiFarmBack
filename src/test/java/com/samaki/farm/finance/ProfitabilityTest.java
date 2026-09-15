package com.samaki.farm.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.cost.entity.Cost;
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
 * FAIDA - cycleProfitability na farmProfitability.
 *
 * Mizunguko na gharama zinaandikwa MOJA KWA MOJA kwenye database: kinachojaribiwa
 * ni HESABU na uanachama wa kipindi, si njia za kurekodi (zina majaribio yao).
 * Chakula kinapita kwenye mutation halisi, kwa sababu kubatilisha ni leja -
 * na leja ndiyo sheria inayojaribiwa.
 */
@DisplayName("Faida ya mzunguko na ya shamba")
class ProfitabilityTest extends IntegrationTest {

    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    private static final String CYCLE_FIELDS = """
            { cycleId label status actualHarvestDate revenue revenueRecorded \
            fingerlingCost fingerlingCostRecorded cycleOperationalCost cycleNetProfit }""";

    private static final String FARM_FIELDS = """
            { farmId farmName fromDate toDate farmRevenue fingerlingCost cycleOperationalCost \
            cycleOperationalCostByCategory { costCategoryId name amount } cycleCosts \
            feedCost reversedFeedPurchasesExcluded farmOperationalCost \
            farmOperationalCostByCategory { costCategoryId name amount } \
            farmCosts farmNetProfit cycleCount incompleteCycleCount capitalTotal \
            cycles""" + CYCLE_FIELDS + " }";

    @Autowired private CycleRepository cycles;
    @Autowired private ProductionUnitRepository units;
    @Autowired private SpeciesRepository species;
    @Autowired private CostRepository costRepository;
    @Autowired private CostCategoryRepository costCategories;
    @Autowired private FarmRepository farms;
    @Autowired private FarmUserRepository farmUsers;
    @Autowired private RoleRepository roles;

    // --------------------------------------------------------- misaada

    private JsonNode cycleProfitability(String token, int cycleId) {
        return graphql(token, "query { cycleProfitability(cycleId: " + cycleId + ") " + CYCLE_FIELDS + " }");
    }

    private JsonNode farmProfitability(String token, int farmId, String from, String to) {
        return graphql(token, "query { farmProfitability(farmId: " + farmId + ", fromDate: \"" + from
                + "\", toDate: \"" + to + "\") " + FARM_FIELDS + " }");
    }

    private JsonNode farmOk(String from, String to) {
        JsonNode res = farmProfitability(adminToken, farmA, from, to);
        assertThat(graphqlErrorCode(res)).as("farmProfitability: %s", res).isNull();
        return res.path("data").path("farmProfitability");
    }

    /** Mzunguko uliofungwa wa shamba A; revenue/fingerling null = haikurekodiwa. */
    private int closedCycle(int unitId, String status, String harvestDate, String revenue, String fingerling) {
        return inTx(() -> {
            Cycle cycle = new Cycle();
            cycle.setUnit(units.findById(unitId).orElseThrow());
            cycle.setSpecies(species.findAll().get(0));
            cycle.setStockingDate(LocalDate.of(2025, 1, 1));
            cycle.setFingerlingsCount(1000);
            cycle.setStatus(status);
            cycle.setActualHarvestDate(LocalDate.parse(harvestDate));
            cycle.setTotalRevenue(revenue == null ? null : new BigDecimal(revenue));
            cycle.setFingerlingCost(fingerling == null ? null : new BigDecimal(fingerling));
            return cycles.save(cycle).getCycleId();
        });
    }

    private int category(String name) {
        return graphql(adminToken, "mutation { createCostCategory(name: \"" + name + "\") { costCategoryId } }")
                .path("data").path("createCostCategory").path("costCategoryId").asInt();
    }

    /** cycleId null = gharama ya shamba zima. */
    private void cost(int farmId, Integer cycleId, int categoryId, String amount, String date) {
        inTx(() -> {
            Cost cost = new Cost();
            cost.setFarm(farms.findByFarmId(farmId).orElseThrow());
            cost.setCycle(cycleId == null ? null : cycles.findByCycleId(cycleId).orElseThrow());
            cost.setCostCategory(costCategories.findByCostCategoryId(categoryId).orElseThrow());
            cost.setAmount(new BigDecimal(amount));
            cost.setCostDate(LocalDate.parse(date));
            return costRepository.save(cost);
        });
    }

    private int feedType() {
        return graphql(adminToken, "mutation { createFeedType(name: \"Pellet\", minAgeMonths: 0, "
                + "maxAgeMonths: 12) { feedTypeId } }").path("data").path("createFeedType").path("feedTypeId").asInt();
    }

    private String feedInput(int feedTypeId, String date, double kg, double unitCost) {
        return "{purchaseDate: \"" + date + "\", feedTypeId: " + feedTypeId + ", quantityKg: " + kg
                + ", unitCost: " + unitCost + ", supplier: \"Duka\"}";
    }

    private int purchase(int feedTypeId, String date, double kg, double unitCost) {
        JsonNode res = graphql(adminToken, "mutation { recordFeedPurchase(input: "
                + feedInput(feedTypeId, date, kg, unitCost) + ") { purchaseId } }");
        assertThat(graphqlErrorCode(res)).as("ununuzi: %s", res).isNull();
        return res.path("data").path("recordFeedPurchase").path("purchaseId").asInt();
    }

    private void asset(String cost, String acquiredDate) {
        int categoryId = graphql(adminToken, "mutation { createAssetCategory(name: \"Mali " + cost
                + "\") { assetCategoryId } }").path("data").path("createAssetCategory").path("assetCategoryId").asInt();
        JsonNode res = graphql(adminToken, "mutation { createAsset(name: \"Kitu\", farmId: " + farmA
                + ", cost: " + cost + ", acquiredDate: \"" + acquiredDate + "\", assetCategoryId: "
                + categoryId + ") { assetId } }");
        assertThat(graphqlErrorCode(res)).as("mali: %s", res).isNull();
    }

    private void setFingerlingCost(int cycleId, String amount) {
        inTx(() -> {
            Cycle cycle = cycles.findByCycleId(cycleId).orElseThrow();
            cycle.setFingerlingCost(new BigDecimal(amount));
            return cycles.save(cycle);
        });
    }

    private void makeWorkerFarmManager() {
        inTx(() -> {
            FarmUser membership = farmUsers.findByUser_UserIdAndFarm_FarmId(workerId, farmA).orElseThrow();
            membership.setRole(roles.findByName("FARM_MANAGER").orElseThrow());
            return farmUsers.save(membership);
        });
        JwtAuthFilter.clearUserCache(workerId);
    }

    private static JsonNode categoryRow(JsonNode rows, String name) {
        for (JsonNode row : rows) {
            if (name.equals(row.path("name").asText())) {
                return row;
            }
        }
        return null;
    }

    // =====================================================================

    @Nested
    @DisplayName("faida ya mzunguko")
    class Mzunguko {

        @Test
        @DisplayName("mapato - vifaranga - gharama za mzunguko; gharama za shamba zima HAZIMO")
        void closedCycleNetProfit() {
            int dawa = category("Dawa");
            int umeme = category("Umeme");
            int cycle = closedCycle(unitA, Cycle.HARVESTED, "2025-05-10", "1000000", "200000");
            cost(farmA, cycle, dawa, "50000", "2025-02-01");
            cost(farmA, cycle, dawa, "30000", "2025-08-01");
            cost(farmA, null, umeme, "400000", "2025-05-01");
            cost(farmA, cycleA, dawa, "7000", "2025-05-01");

            JsonNode res = cycleProfitability(adminToken, cycle);

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode row = res.path("data").path("cycleProfitability");
            assertThat(row.path("status").asText()).isEqualTo("HARVESTED");
            assertThat(row.path("actualHarvestDate").asText()).isEqualTo("2025-05-10");
            assertThat(row.path("revenue").asDouble()).isEqualTo(1_000_000.0);
            assertThat(row.path("revenueRecorded").asBoolean()).isTrue();
            assertThat(row.path("fingerlingCost").asDouble()).isEqualTo(200_000.0);
            assertThat(row.path("fingerlingCostRecorded").asBoolean()).isTrue();
            assertThat(row.path("cycleOperationalCost").asDouble()).isEqualTo(80_000.0);
            assertThat(row.path("cycleNetProfit").asDouble()).isEqualTo(720_000.0);
        }

        @Test
        @DisplayName("mzunguko UNAOENDELEA: profit null, status ACTIVE - si hitilafu")
        void activeCycleHasNullProfit() {
            int dawa = category("Dawa");
            setFingerlingCost(cycleA, "150000");
            cost(farmA, cycleA, dawa, "4000", "2025-05-01");

            JsonNode res = cycleProfitability(adminToken, cycleA);

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode row = res.path("data").path("cycleProfitability");
            assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
            assertThat(row.path("revenue").isNull()).isTrue();
            assertThat(row.path("cycleNetProfit").isNull()).isTrue();
            assertThat(row.path("actualHarvestDate").isNull()).isTrue();
            assertThat(row.path("fingerlingCost").asDouble()).isEqualTo(150_000.0);
            assertThat(row.path("cycleOperationalCost").asDouble()).isEqualTo(4_000.0);
        }

        @Test
        @DisplayName("NULL ya mapato na vifaranga = 0 kwenye hesabu, bendera ni false")
        void nullInputsAreZeroButFlagged() {
            int cycle = closedCycle(unitA, Cycle.FAILED, "2025-05-10", null, null);

            JsonNode row = cycleProfitability(adminToken, cycle).path("data").path("cycleProfitability");

            assertThat(row.path("revenue").asDouble()).isZero();
            assertThat(row.path("revenueRecorded").asBoolean()).isFalse();
            assertThat(row.path("fingerlingCost").asDouble()).isZero();
            assertThat(row.path("fingerlingCostRecorded").asBoolean()).isFalse();
            assertThat(row.path("cycleNetProfit").asDouble()).isZero();
        }

        /** Njia halisi: tukio la SOLD, kisha closeCycle inahifadhi total_revenue. */
        @Test
        @DisplayName("mapato ni total_revenue iliyohifadhiwa na closeCycle")
        void revenueComesFromCloseCycle() {
            LocalDate today = LocalDate.now(EAT);
            JsonNode sold = graphql(adminToken, "mutation { recordHarvestEvent(cycleId: " + cycleA
                    + ", eventDate: \"" + today.minusDays(1) + "\", fishCount: 100, weightKg: 50, "
                    + "reason: \"SOLD\", saleAmount: 400000) { harvestEventId } }");
            assertThat(graphqlErrorCode(sold)).as("tukio: %s", sold).isNull();
            JsonNode closed = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleA
                    + ", outcome: \"HARVESTED\", actualHarvestDate: \"" + today + "\") { status } }");
            assertThat(graphqlErrorCode(closed)).as("kufunga: %s", closed).isNull();

            JsonNode row = cycleProfitability(adminToken, cycleA).path("data").path("cycleProfitability");

            assertThat(row.path("revenue").asDouble()).isEqualTo(400_000.0);
            assertThat(row.path("revenueRecorded").asBoolean()).isTrue();
            assertThat(row.path("cycleNetProfit").asDouble()).isEqualTo(400_000.0);
        }
    }

    @Nested
    @DisplayName("faida ya shamba")
    class Shamba {

        /**
         * Kipindi 2025-04-01..2025-06-30. Kila mstari hapa chini upo kwa ajili ya
         * sheria moja ya uanachama wa kipindi.
         */
        @Test
        @DisplayName("hesabu kamili: kipindi, chakula bila kubatilishwa, aina, mtaji kando")
        void fullPeriodBreakdown() {
            int dawa = category("Dawa");
            int chakula = category("Chakula");
            int umeme = category("Umeme");

            // Mzunguko ndani ya kipindi; gharama yake ya NJE ya kipindi bado inahesabiwa.
            int c1 = closedCycle(unitA, Cycle.HARVESTED, "2025-05-10", "1000000", "200000");
            cost(farmA, c1, dawa, "50000", "2025-02-01");
            cost(farmA, c1, chakula, "30000", "2025-05-05");
            // Mpaka wa mwisho, data isiyorekodiwa.
            int c2 = closedCycle(unitA, Cycle.FAILED, "2025-06-30", null, null);
            cost(farmA, c2, dawa, "10000", "2025-06-01");
            // Mavuno NJE ya kipindi: gharama yake ya NDANI ya kipindi haihesabiwi.
            int c3 = closedCycle(unitA, Cycle.HARVESTED, "2025-07-01", "999", "999");
            cost(farmA, c3, dawa, "7000", "2025-05-01");
            // Unaoendelea: gharama zake haziingii kipindi chochote.
            cost(farmA, cycleA, dawa, "4000", "2025-05-01");

            // Shamba zima kwa costDate (mpaka wa mwanzo umo; siku moja kabla haimo).
            cost(farmA, null, umeme, "100000", "2025-04-01");
            cost(farmA, null, umeme, "20000", "2025-06-15");
            cost(farmA, null, chakula, "5000", "2025-05-01");
            cost(farmA, null, umeme, "999000", "2025-03-31");

            // Chakula: uliorekebishwa (wa zamani nje), uliobatilishwa (nje), nje ya kipindi.
            int pellet = feedType();
            int original = purchase(pellet, "2025-05-01", 100, 1000);
            JsonNode corrected = graphql(adminToken, "mutation { correctFeedPurchase(purchaseId: " + original
                    + ", input: " + feedInput(pellet, "2025-05-01", 80, 1000) + ") { purchaseId } }");
            assertThat(graphqlErrorCode(corrected)).as("kurekebisha: %s", corrected).isNull();
            int reversed = purchase(pellet, "2025-06-01", 10, 500);
            JsonNode reverse = graphql(adminToken,
                    "mutation { reverseFeedPurchase(purchaseId: " + reversed + ") { purchaseId } }");
            assertThat(graphqlErrorCode(reverse)).as("kubatilisha: %s", reverse).isNull();
            purchase(pellet, "2025-03-01", 50, 1000);

            // Mtaji: ndani na nje ya kipindi.
            asset("3000000", "2025-05-20");
            asset("1000", "2024-01-01");

            JsonNode farm = farmOk("2025-04-01", "2025-06-30");

            assertThat(farm.path("farmId").asInt()).isEqualTo(farmA);
            assertThat(farm.path("farmName").asText()).isEqualTo("Dev Farm A");
            assertThat(farm.path("fromDate").asText()).isEqualTo("2025-04-01");
            assertThat(farm.path("toDate").asText()).isEqualTo("2025-06-30");

            assertThat(farm.path("farmRevenue").asDouble()).isEqualTo(1_000_000.0);
            assertThat(farm.path("fingerlingCost").asDouble()).isEqualTo(200_000.0);
            assertThat(farm.path("cycleOperationalCost").asDouble()).isEqualTo(90_000.0);
            assertThat(farm.path("cycleCosts").asDouble()).isEqualTo(290_000.0);

            assertThat(farm.path("feedCost").asDouble()).isEqualTo(80_000.0);
            assertThat(farm.path("reversedFeedPurchasesExcluded").asInt()).isEqualTo(2);

            assertThat(farm.path("farmOperationalCost").asDouble()).isEqualTo(125_000.0);
            JsonNode farmByCategory = farm.path("farmOperationalCostByCategory");
            assertThat(farmByCategory).hasSize(2);
            assertThat(categoryRow(farmByCategory, "Umeme").path("amount").asDouble()).isEqualTo(120_000.0);
            assertThat(categoryRow(farmByCategory, "Chakula").path("amount").asDouble()).isEqualTo(5_000.0);

            JsonNode cycleByCategory = farm.path("cycleOperationalCostByCategory");
            assertThat(cycleByCategory).hasSize(2);
            assertThat(categoryRow(cycleByCategory, "Dawa").path("amount").asDouble()).isEqualTo(60_000.0);
            assertThat(categoryRow(cycleByCategory, "Chakula").path("amount").asDouble()).isEqualTo(30_000.0);

            assertThat(farm.path("farmCosts").asDouble()).isEqualTo(495_000.0);
            assertThat(farm.path("farmNetProfit").asDouble()).isEqualTo(505_000.0);
            // Mtaji HAUMO kwenye farmCosts wala farmNetProfit.
            assertThat(farm.path("capitalTotal").asDouble()).isEqualTo(3_000_000.0);

            assertThat(farm.path("cycleCount").asInt()).isEqualTo(2);
            assertThat(farm.path("incompleteCycleCount").asInt()).isEqualTo(1);
            JsonNode rows = farm.path("cycles");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).path("cycleId").asInt()).isEqualTo(c1);
            assertThat(rows.get(0).path("cycleNetProfit").asDouble()).isEqualTo(720_000.0);
            assertThat(rows.get(1).path("cycleId").asInt()).isEqualTo(c2);
            assertThat(rows.get(1).path("revenueRecorded").asBoolean()).isFalse();
            assertThat(rows.get(1).path("fingerlingCostRecorded").asBoolean()).isFalse();
            assertThat(rows.get(1).path("cycleNetProfit").asDouble()).isEqualTo(-10_000.0);
        }

        @Test
        @DisplayName("kipindi kisicho na chochote ni sifuri, si hitilafu")
        void emptyPeriodIsZero() {
            JsonNode farm = farmOk("2020-01-01", "2020-12-31");

            assertThat(farm.path("farmRevenue").asDouble()).isZero();
            assertThat(farm.path("farmCosts").asDouble()).isZero();
            assertThat(farm.path("farmNetProfit").asDouble()).isZero();
            assertThat(farm.path("capitalTotal").asDouble()).isZero();
            assertThat(farm.path("cycles")).isEmpty();
            assertThat(farm.path("farmOperationalCostByCategory")).isEmpty();
            assertThat(farm.path("cycleOperationalCostByCategory")).isEmpty();
        }

        @Test
        @DisplayName("fromDate baada ya toDate, au tarehe isiyosomeka, ni VALIDATION_ERROR")
        void rejectsBadPeriod() {
            assertThat(graphqlErrorCode(farmProfitability(adminToken, farmA, "2025-07-01", "2025-06-30")))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(farmProfitability(adminToken, farmA, "01/07/2025", "2025-06-30")))
                    .isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("ruhusa")
    class Ruhusa {

        @Test
        @DisplayName("WORKER na VIEWER hawana view_finance - FORBIDDEN kwa zote mbili")
        void workerAndViewerForbidden() {
            for (String token : new String[] {workerToken, viewerToken, noroleToken}) {
                assertThat(graphqlErrorCode(cycleProfitability(token, cycleA))).isEqualTo("FORBIDDEN");
                assertThat(graphqlErrorCode(farmProfitability(token, farmA, "2025-01-01", "2025-12-31")))
                        .isEqualTo("FORBIDDEN");
            }
        }

        @Test
        @DisplayName("shamba au mzunguko asilo lake ni FORBIDDEN")
        void foreignFarmForbidden() {
            int cycleB = closedCycle(unitB, Cycle.HARVESTED, "2025-05-10", "1000", "100");

            assertThat(graphqlErrorCode(farmProfitability(adminToken, farmB, "2025-01-01", "2025-12-31")))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(cycleProfitability(adminToken, cycleB))).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("mzunguko usiojulikana ni VALIDATION_ERROR")
        void unknownCycle() {
            assertThat(graphqlErrorCode(cycleProfitability(adminToken, 99999))).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("FARM_MANAGER anayo view_finance")
        void farmManagerAllowed() {
            makeWorkerFarmManager();

            assertThat(graphqlErrorCode(cycleProfitability(workerToken, cycleA))).isNull();
            assertThat(graphqlErrorCode(farmProfitability(workerToken, farmA, "2025-01-01", "2025-12-31")))
                    .isNull();
        }
    }
}
