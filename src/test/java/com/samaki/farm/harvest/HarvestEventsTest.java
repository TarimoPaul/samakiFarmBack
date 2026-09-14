package com.samaki.farm.harvest;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.cycle.repository.CycleRepository;
import com.samaki.farm.feed.entity.FeedType;
import com.samaki.farm.feed.entity.FeedingLog;
import com.samaki.farm.productionunit.repository.ProductionUnitRepository;
import com.samaki.farm.species.repository.SpeciesRepository;
import com.samaki.farm.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MATUKIO YA MAVUNO (V25/V26) - SOLD / DIED / REMOVED.
 *
 * Matukio haya ndiyo chanzo cha idadi, uzito, vifo, mapato na kiwango cha
 * kuishi cha kila mzunguko (closeCycle inayajumlisha - angalia
 * CycleLifecycleTest kwa jumla zenyewe). Faili hili linalinda MLANGO:
 *
 *   * kila uga unathibitishwa - sababu tatu tu, SOLD ina uzito na kiasi,
 *     vifo havina mapato, tarehe si ya baadaye wala kabla ya kuweka;
 *   * mzunguko uliofungwa ni wa MWISHO - hakuna kurekodi wala kufuta;
 *   * `record_harvest` ni ya OWNER/FARM_MANAGER pekee, na shamba ni lake;
 *   * FEDHA (saleAmount, totalRevenue, fingerlingCost) inarudi null bila
 *     `view_finance` - kwenye jibu la server, si kwenye UI.
 *
 * Wahusika wa fixture: admin = OWNER (record_harvest + view_finance),
 * worker = WORKER (view_dashboard pekee), viewer = VIEWER, workerB =
 * WORKER wa shamba B.
 */
@DisplayName("H - Matukio ya mavuno")
class HarvestEventsTest extends IntegrationTest {

    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    @Autowired private CycleRepository cycles;
    @Autowired private ProductionUnitRepository units;
    @Autowired private SpeciesRepository species;
    @Autowired private EntityManager entityManager;

    /** Mzunguko wa shamba A, uliowekwa 2025-01-10, vifaranga 1000. */
    private int cycleId;

    @BeforeEach
    void stockCycle() {
        int speciesId = species.findAll().get(0).getSpeciesId();
        JsonNode res = graphql(adminToken, "mutation { createCycle(input: {unitId: " + unitA
                + ", speciesId: " + speciesId + ", stockingDate: \"2025-01-10\""
                + ", fingerlingsCount: 1000, fingerlingCost: 300000}) { cycleId } }");
        assertThat(graphqlErrorCode(res)).isNull();
        cycleId = res.path("data").path("createCycle").path("cycleId").asInt();
    }

    // --------------------------------------------------------- misaada

    private static final String EVENT_FIELDS =
            "{ harvestEventId cycleId eventDate fishCount weightKg reason saleAmount }";

    private JsonNode record(String token, int cycle, String date, String fish, String kg,
                            String reason, String amount) {
        String weight = kg == null ? "" : ", weightKg: " + kg;
        String sale = amount == null ? "" : ", saleAmount: " + amount;
        return graphql(token, "mutation { recordHarvestEvent(cycleId: " + cycle
                + ", eventDate: \"" + date + "\", fishCount: " + fish + weight
                + ", reason: \"" + reason + "\"" + sale + ") " + EVENT_FIELDS + " }");
    }

    private JsonNode record(String date, String fish, String kg, String reason, String amount) {
        return record(adminToken, cycleId, date, fish, kg, reason, amount);
    }

    private int recordOk(String date, String fish, String kg, String reason, String amount) {
        JsonNode res = record(date, fish, kg, reason, amount);
        assertThat(graphqlErrorCode(res)).as("tukio la mfano: %s", res).isNull();
        return res.path("data").path("recordHarvestEvent").path("harvestEventId").asInt();
    }

    private JsonNode list(String token, int cycle) {
        return graphql(token, "query { harvestEvents(cycleId: " + cycle + ") " + EVENT_FIELDS + " }");
    }

    private JsonNode events() {
        JsonNode res = list(adminToken, cycleId);
        assertThat(graphqlErrorCode(res)).isNull();
        return res.path("data").path("harvestEvents");
    }

    private JsonNode delete(String token, int harvestEventId) {
        return graphql(token, "mutation { deleteHarvestEvent(harvestEventId: " + harvestEventId + ") }");
    }

    private void closeHarvested(String date) {
        JsonNode res = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleId
                + ", outcome: \"HARVESTED\", actualHarvestDate: \"" + date + "\") { status } }");
        assertThat(graphqlErrorCode(res)).as("kufunga: %s", res).isNull();
    }

    /** Mzunguko wa SHAMBA B, uliopandwa moja kwa moja - hakuna mwenye record_harvest huko. */
    private int cycleOnFarmB() {
        return inTx(() -> {
            Cycle cycle = new Cycle();
            cycle.setUnit(units.findByFarm_FarmId(farmB).get(0));
            cycle.setSpecies(species.findAll().get(0));
            cycle.setStockingDate(LocalDate.of(2025, 1, 10));
            cycle.setFingerlingsCount(500);
            cycle.setStatus(Cycle.ACTIVE);
            return cycles.save(cycle).getCycleId();
        });
    }

    // =====================================================================

    @Nested
    @DisplayName("kurekodi na kusoma")
    class RecordAndList {

        @Test
        @DisplayName("SOLD inahifadhiwa na kurudishwa kamili")
        void recordsASale() {
            JsonNode res = record("2025-07-01", "600", "300.5", "SOLD", "1800000");

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode event = res.path("data").path("recordHarvestEvent");
            assertThat(event.path("cycleId").asInt()).isEqualTo(cycleId);
            assertThat(event.path("eventDate").asText()).isEqualTo("2025-07-01");
            assertThat(event.path("fishCount").asInt()).isEqualTo(600);
            assertThat(event.path("weightKg").asDouble()).isEqualTo(300.5);
            assertThat(event.path("reason").asText()).isEqualTo("SOLD");
            assertThat(event.path("saleAmount").asDouble()).isEqualTo(1_800_000.0);
        }

        /** Herufi ndogo zinakubalika na zinahifadhiwa kwa jina rasmi. */
        @Test
        @DisplayName("sababu inasomwa bila kujali herufi kubwa/ndogo")
        void reasonIsCaseInsensitive() {
            JsonNode res = record("2025-07-01", "10", null, " died ", null);

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("recordHarvestEvent").path("reason").asText())
                    .isEqualTo("DIED");
        }

        @Test
        @DisplayName("orodha ni ya mzunguko huu, mapya kwanza")
        void listsNewestFirst() {
            recordOk("2025-06-01", "50", null, "DIED", null);
            recordOk("2025-07-01", "300", "150", "SOLD", "450000");
            recordOk("2025-06-15", "20", "8", "REMOVED", null);

            JsonNode events = events();

            assertThat(events).hasSize(3);
            assertThat(events.get(0).path("eventDate").asText()).isEqualTo("2025-07-01");
            assertThat(events.get(1).path("eventDate").asText()).isEqualTo("2025-06-15");
            assertThat(events.get(2).path("eventDate").asText()).isEqualTo("2025-06-01");
            // Mzunguko wa fixture hauna matukio - orodha si ya shamba zima.
            assertThat(list(adminToken, cycleA).path("data").path("harvestEvents")).isEmpty();
        }

        /** Historia inabaki inasomeka baada ya kufunga. */
        @Test
        @DisplayName("matukio ya mzunguko uliofungwa bado yanasomeka")
        void closedCycleEventsStayReadable() {
            recordOk("2025-07-01", "600", "300", "SOLD", "900000");
            closeHarvested("2025-07-15");

            assertThat(events()).hasSize(1);
        }

        /** V19 iliruhusu kuishi > 1.0; tukio moja linalozidi vifaranga halikataliwi. */
        @Test
        @DisplayName("idadi inayozidi vifaranga waliowekwa inakubalika")
        void countAboveStockedIsAccepted() {
            assertThat(graphqlErrorCode(record("2025-07-01", "1200", "600", "SOLD", "1500000")))
                    .isNull();
        }
    }

    /** Kila uga una sheria yake - na kila sheria inalinda namba ya kufunga. */
    @Nested
    @DisplayName("uthibitisho wa uga")
    class Validation {

        @Test
        @DisplayName("sababu isiyojulikana inakataliwa - ujumbe unaorodhesha tatu")
        void refusesUnknownReason() {
            JsonNode res = record("2025-07-01", "10", "5", "STOLEN", null);

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("SOLD", "DIED", "REMOVED");
            assertThat(graphqlErrorCode(record("2025-07-01", "10", "5", "", null)))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("idadi ya sifuri au hasi inakataliwa")
        void refusesNonPositiveFishCount() {
            assertThat(graphqlErrorCode(record("2025-07-01", "0", null, "DIED", null)))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record("2025-07-01", "-5", null, "DIED", null)))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("SOLD bila uzito, au kwa uzito wa sifuri, inakataliwa")
        void soldRequiresWeight() {
            assertThat(graphqlErrorCode(record("2025-07-01", "100", null, "SOLD", "300000")))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record("2025-07-01", "100", "0", "SOLD", "300000")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("SOLD bila kiasi, au kwa kiasi cha sifuri, inakataliwa")
        void soldRequiresSaleAmount() {
            assertThat(graphqlErrorCode(record("2025-07-01", "100", "50", "SOLD", null)))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record("2025-07-01", "100", "50", "SOLD", "0")))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record("2025-07-01", "100", "50", "SOLD", "-10")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        /** Mizoga haipimwi - uzito ni hiari kwa DIED/REMOVED. */
        @Test
        @DisplayName("DIED na REMOVED zinakubalika bila uzito")
        void weightOptionalForDiedAndRemoved() {
            JsonNode died = record("2025-07-01", "40", null, "DIED", null);
            JsonNode removed = record("2025-07-01", "30", null, "REMOVED", null);

            assertThat(graphqlErrorCode(died)).isNull();
            assertThat(graphqlErrorCode(removed)).isNull();
            assertThat(died.path("data").path("recordHarvestEvent").path("weightKg").isNull()).isTrue();
        }

        /** Uzito ukitolewa kwa DIED/REMOVED, lazima uwe kipimo halisi (> 0). */
        @Test
        @DisplayName("uzito wa sifuri unakataliwa hata kwa DIED/REMOVED")
        void givenWeightMustBePositive() {
            assertThat(graphqlErrorCode(record("2025-07-01", "40", "0", "DIED", null)))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record("2025-07-01", "40", "-2", "REMOVED", null)))
                    .isEqualTo("VALIDATION_ERROR");
        }

        /**
         * Fomu nyingi hutuma 0 kwa uga tupu - inakubalika na kuhifadhiwa kama
         * null. Kiasi HALISI kwenye kifo kinakataliwa: kingeingia kwenye
         * jumla ya mapato.
         */
        @Test
        @DisplayName("kiasi cha mauzo: 0 kwa DIED ni null, kiasi halisi kinakataliwa")
        void saleAmountOnlyForSold() {
            JsonNode zero = record("2025-07-01", "40", null, "DIED", "0");
            assertThat(graphqlErrorCode(zero)).isNull();
            assertThat(zero.path("data").path("recordHarvestEvent").path("saleAmount").isNull())
                    .isTrue();

            assertThat(graphqlErrorCode(record("2025-07-01", "40", null, "DIED", "5000")))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record("2025-07-01", "40", "10", "REMOVED", "5000")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("tarehe kabla ya kuweka inakataliwa")
        void refusesDateBeforeStocking() {
            assertThat(graphqlErrorCode(record("2025-01-09", "10", null, "DIED", null)))
                    .isEqualTo("VALIDATION_ERROR");
            // Siku ya kuweka yenyewe inakubalika (vifo vya usafirishaji).
            assertThat(graphqlErrorCode(record("2025-01-10", "10", null, "DIED", null))).isNull();
        }

        @Test
        @DisplayName("tarehe ya baadaye inakataliwa; leo ya EAT inakubalika")
        void refusesFutureDate() {
            String tomorrow = LocalDate.now(EAT).plusDays(1).toString();
            String today = LocalDate.now(EAT).toString();

            assertThat(graphqlErrorCode(record(tomorrow, "10", null, "DIED", null)))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(record(today, "10", null, "DIED", null))).isNull();
        }

        @Test
        @DisplayName("tarehe isiyosomeka inakataliwa")
        void refusesMalformedDate() {
            assertThat(graphqlErrorCode(record("01/07/2025", "10", null, "DIED", null)))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("mzunguko usiojulikana unakataliwa")
        void refusesUnknownCycle() {
            assertThat(graphqlErrorCode(record(adminToken, 999_999, "2025-07-01", "10", null,
                    "DIED", null))).isEqualTo("VALIDATION_ERROR");
        }

        /** Hakuna kilichohifadhiwa kutoka majaribio yote yaliyokataliwa hapo juu. */
        @Test
        @DisplayName("tukio lililokataliwa haliachi safu")
        void rejectedEventLeavesNothing() {
            record("2025-07-01", "100", null, "SOLD", "300000");
            record("2025-07-01", "40", null, "DIED", "5000");

            assertThat(events()).isEmpty();
        }
    }

    /**
     * KUFUNGA NI KWA MWISHO. Jumla za kufunga ni picha ya matukio ya siku
     * ile; tukio jipya au lililofutwa lingeziacha zisilingane na historia.
     */
    @Nested
    @DisplayName("mzunguko uliofungwa")
    class ClosedCycle {

        @Test
        @DisplayName("kurekodi kwenye mzunguko uliofungwa kunakataliwa")
        void refusesRecordAfterClose() {
            recordOk("2025-07-01", "600", "300", "SOLD", "900000");
            closeHarvested("2025-07-15");

            JsonNode res = record("2025-07-10", "50", "25", "SOLD", "75000");

            assertThat(graphqlErrorCode(res)).isEqualTo("CYCLE_ALREADY_CLOSED");
            assertThat(events()).hasSize(1);
        }

        @Test
        @DisplayName("kufuta kwenye mzunguko uliofungwa kunakataliwa, na tukio linabaki")
        void refusesDeleteAfterClose() {
            int eventId = recordOk("2025-07-01", "600", "300", "SOLD", "900000");
            closeHarvested("2025-07-15");

            assertThat(graphqlErrorCode(delete(adminToken, eventId)))
                    .isEqualTo("CYCLE_ALREADY_CLOSED");
            assertThat(events()).hasSize(1);
        }

        @Test
        @DisplayName("mzunguko uliofeli nao ni wa mwisho")
        void failedCycleIsFinalToo() {
            JsonNode closed = graphql(adminToken, "mutation { closeCycle(cycleId: " + cycleId
                    + ", outcome: \"FAILED\", actualHarvestDate: \"2025-03-01\") { status } }");
            assertThat(graphqlErrorCode(closed)).isNull();

            assertThat(graphqlErrorCode(record("2025-03-01", "10", null, "DIED", null)))
                    .isEqualTo("CYCLE_ALREADY_CLOSED");
        }
    }

    /** Soft-delete ya tukio lililokosewa - njia pekee ya kulirekebisha. */
    @Nested
    @DisplayName("kufuta tukio")
    class Deleting {

        @Test
        @DisplayName("tukio linafutwa kwenye orodha, na safu inabaki (soft)")
        void softDeletesWhileActive() {
            int keep = recordOk("2025-07-01", "300", "150", "SOLD", "450000");
            int mistake = recordOk("2025-07-02", "3000", "1500", "SOLD", "4500000");

            JsonNode res = delete(adminToken, mistake);

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("deleteHarvestEvent").asBoolean()).isTrue();
            JsonNode events = events();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).path("harvestEventId").asInt()).isEqualTo(keep);

            // SOFT: safu bado ipo, ikiwa na aliyeifuta.
            assertThat(deletedRow(mistake)).isTrue();
        }

        @Test
        @DisplayName("kufuta lililokwisha futwa kunakataliwa")
        void refusesDoubleDelete() {
            int eventId = recordOk("2025-07-01", "40", null, "DIED", null);
            assertThat(graphqlErrorCode(delete(adminToken, eventId))).isNull();

            assertThat(graphqlErrorCode(delete(adminToken, eventId))).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("tukio lisilojulikana linakataliwa")
        void refusesUnknownEvent() {
            assertThat(graphqlErrorCode(delete(adminToken, 999_999))).isEqualTo("VALIDATION_ERROR");
        }
    }

    /** Safu ya tukio ipo database na imewekwa alama ya kufutwa (na nani). */
    private boolean deletedRow(int harvestEventId) {
        return inTx(() -> {
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT is_deleted, deleted_by FROM harvest_events WHERE harvest_event_id = :id")
                    .setParameter("id", harvestEventId)
                    .getSingleResult();
            return Boolean.TRUE.equals(row[0]) && row[1] != null;
        });
    }

    /**
     * `record_harvest` (V26): OWNER/FARM_MANAGER pekee. Kusoma ni
     * `view_dashboard`. Shamba ni la mwombaji, kila mara.
     */
    @Nested
    @DisplayName("ruhusa na shamba")
    class Permissions {

        @Test
        @DisplayName("WORKER na VIEWER hawawezi kurekodi")
        void workerAndViewerCannotRecord() {
            assertThat(graphqlErrorCode(record(workerToken, cycleId, "2025-07-01", "10", null,
                    "DIED", null))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(record(viewerToken, cycleId, "2025-07-01", "10", null,
                    "DIED", null))).isEqualTo("FORBIDDEN");
            assertThat(events()).isEmpty();
        }

        @Test
        @DisplayName("WORKER hawezi kufuta")
        void workerCannotDelete() {
            int eventId = recordOk("2025-07-01", "40", null, "DIED", null);

            assertThat(graphqlErrorCode(delete(workerToken, eventId))).isEqualTo("FORBIDDEN");
            assertThat(events()).hasSize(1);
        }

        @Test
        @DisplayName("WORKER anasoma matukio (view_dashboard)")
        void workerCanList() {
            recordOk("2025-07-01", "40", null, "DIED", null);

            JsonNode res = list(workerToken, cycleId);

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("harvestEvents")).hasSize(1);
        }

        @Test
        @DisplayName("mzunguko wa shamba jingine: kusoma ni FORBIDDEN")
        void refusesListingAnotherFarmsCycle() {
            assertThat(graphqlErrorCode(list(workerBToken, cycleId))).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("mzunguko wa shamba jingine: kurekodi ni FORBIDDEN")
        void refusesRecordingOnAnotherFarmsCycle() {
            int foreign = cycleOnFarmB();

            assertThat(graphqlErrorCode(record(adminToken, foreign, "2025-07-01", "10", null,
                    "DIED", null))).isEqualTo("FORBIDDEN");
        }

        /**
         * Shamba LINAKAGULIWA KABLA ya hali: mzunguko uliofungwa wa shamba
         * jingine ni FORBIDDEN, si CYCLE_ALREADY_CLOSED - jibu la pili
         * lingevujisha hali ya mizunguko ya mashamba mengine.
         */
        @Test
        @DisplayName("mzunguko uliofungwa wa shamba jingine ni FORBIDDEN, si CLOSED")
        void farmIsCheckedBeforeStatus() {
            int foreign = cycleOnFarmB();
            inTx(() -> {
                Cycle cycle = cycles.findByCycleId(foreign).orElseThrow();
                cycle.setStatus(Cycle.HARVESTED);
                return cycles.save(cycle);
            });

            assertThat(graphqlErrorCode(record(adminToken, foreign, "2025-07-01", "10", null,
                    "DIED", null))).isEqualTo("FORBIDDEN");
        }
    }

    /**
     * FEDHA INAFICHWA NA SERVER. Bila `view_finance`, saleAmount,
     * totalRevenue na fingerlingCost zinarudi null - kila njia inayozifikia.
     * Safu nyingine zote zinabaki: kujua samaki 600 wameuzwa ni kazi ya
     * shambani; kwa bei gani si.
     */
    @Nested
    @DisplayName("ufichaji wa fedha (view_finance)")
    class FinanceMasking {

        @Test
        @DisplayName("saleAmount: null kwa WORKER, kiasi kwa OWNER")
        void saleAmountMaskedForWorker() {
            recordOk("2025-07-01", "600", "300", "SOLD", "1800000");

            JsonNode asWorker = list(workerToken, cycleId).path("data").path("harvestEvents").get(0);
            assertThat(asWorker.path("saleAmount").isNull()).isTrue();
            // Kila kitu kingine kinabaki.
            assertThat(asWorker.path("fishCount").asInt()).isEqualTo(600);
            assertThat(asWorker.path("weightKg").asDouble()).isEqualTo(300.0);
            assertThat(asWorker.path("reason").asText()).isEqualTo("SOLD");

            JsonNode asOwner = events().get(0);
            assertThat(asOwner.path("saleAmount").asDouble()).isEqualTo(1_800_000.0);
        }

        @Test
        @DisplayName("totalRevenue na fingerlingCost: null kwa WORKER na VIEWER")
        void cycleMoneyMaskedWithoutViewFinance() {
            recordOk("2025-07-01", "600", "300", "SOLD", "1800000");
            closeHarvested("2025-07-15");

            String query = "query { cycles { cycleId harvestedCount totalRevenue fingerlingCost } }";
            for (String token : new String[]{workerToken, viewerToken}) {
                JsonNode cycle = find(graphql(token, query).path("data").path("cycles"));
                assertThat(cycle.path("harvestedCount").asInt()).isEqualTo(600);
                assertThat(cycle.path("totalRevenue").isNull()).isTrue();
                assertThat(cycle.path("fingerlingCost").isNull()).isTrue();
            }

            JsonNode asOwner = find(graphql(adminToken, query).path("data").path("cycles"));
            assertThat(asOwner.path("totalRevenue").asDouble()).isEqualTo(1_800_000.0);
            assertThat(asOwner.path("fingerlingCost").asDouble()).isEqualTo(300_000.0);
        }

        /**
         * Kufichwa ni kwa UGA, si kwa query: `Cycle` inafikiwa pia kupitia
         * `feedingLogs { cycle }` - njia ya kila siku ya WORKER. Kuficha
         * ndani ya `cycles` pekee kungeiacha njia hii ikitangaza bei ya
         * vifaranga. Rekodi ya ulishaji inapandwa moja kwa moja: kinachojaribiwa
         * ni njia ya kusoma, si kulisha.
         */
        @Test
        @DisplayName("ufichaji unafuata uga - hata kupitia FeedingLog.cycle")
        void maskingFollowsTheField() {
            inTx(() -> {
                FeedType feed = new FeedType();
                feed.setName("Test Starter");
                feed.setMinAgeMonths(0);
                feed.setMaxAgeMonths(6);
                entityManager.persist(feed);

                FeedingLog log = new FeedingLog();
                log.setCycle(cycles.findByCycleId(cycleId).orElseThrow());
                log.setFeedType(feed);
                log.setLogDate(LocalDate.of(2025, 2, 1));
                log.setQuantityKg(new BigDecimal("2.5"));
                entityManager.persist(log);
                return log;
            });
            String query = "query { feedingLogs(cycleId: " + cycleId + ") { cycle { cycleId fingerlingCost } } }";

            JsonNode asWorker = graphql(workerToken, query);
            assertThat(graphqlErrorCode(asWorker)).isNull();
            assertThat(asWorker.path("data").path("feedingLogs").get(0)
                    .path("cycle").path("fingerlingCost").isNull()).isTrue();

            JsonNode asOwner = graphql(adminToken, query);
            assertThat(asOwner.path("data").path("feedingLogs").get(0)
                    .path("cycle").path("fingerlingCost").asDouble()).isEqualTo(300_000.0);
        }

        private JsonNode find(JsonNode cyclesList) {
            for (JsonNode cycle : cyclesList) {
                if (cycle.path("cycleId").asInt() == cycleId) {
                    return cycle;
                }
            }
            throw new AssertionError("Mzunguko " + cycleId + " haupo kwenye " + cyclesList);
        }
    }
}
