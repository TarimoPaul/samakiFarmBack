package com.samaki.farm.waterquality;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI B - Ubora wa maji.
 *
 * Module hii ilithibitishwa kwa curl kwa mkono siku ilipojengwa. Hapa
 * uthibitisho ule ule unakuwa wa kudumu - hasa D-1 (kuvuja kati ya
 * mashamba), ambayo ndiyo hitilafu mbaya zaidi iliyowahi kupatikana
 * kwenye backend hii na ambayo hakuna kilichokuwa kikiizuia isirudi.
 */
@DisplayName("B - Ubora wa maji")
class WaterQualityRegressionTest extends IntegrationTest {

    private String logMutation(int unitId, String fields) {
        return "mutation { logWaterQuality(input: {unitId: " + unitId + (fields.isBlank() ? "" : ", " + fields)
                + "}) { logId ph temperature oxygen ammonia notes recordedByName } }";
    }

    @Nested
    @DisplayName("njia ya kuandika")
    class WritePath {

        @Test
        @DisplayName("WORKER anarekodi kipimo, na kinasomeka tena kikiwa kizima")
        void workerLogsAndReadsBack() {
            JsonNode created = graphql(workerToken, logMutation(unitA,
                    "logDate: \"2026-09-01\", ph: 7.2, temperature: 26.5, oxygen: 6.4, ammonia: 0.05, "
                            + "notes: \"Asubuhi\""));
            JsonNode log = created.path("data").path("logWaterQuality");

            assertThat(graphqlErrorCode(created)).isNull();
            assertThat(log.path("ph").asDouble()).isEqualTo(7.2);
            assertThat(log.path("oxygen").asDouble()).isEqualTo(6.4);
            assertThat(log.path("ammonia").asDouble()).isEqualTo(0.05);
            assertThat(log.path("recordedByName").asText()).isEqualTo("Dev Worker");

            JsonNode listed = graphql(workerToken,
                    "query { waterQualityLogs { logId ph ammonia unit { unitId } } }")
                    .path("data").path("waterQualityLogs");
            assertThat(listed).hasSize(1);
            assertThat(listed.get(0).path("unit").path("unitId").asInt()).isEqualTo(unitA);
        }

        @Test
        @DisplayName("kuchuja kwa kitengo na kwa mzunguko kunatoa kipimo kile kile")
        void filtersByUnitAndCycle() {
            graphql(workerToken, logMutation(unitA, "ph: 7.0"));

            assertThat(graphql(workerToken,
                    "query { waterQualityLogs(unitId: " + unitA + ") { logId } }")
                    .path("data").path("waterQualityLogs")).hasSize(1);
            // Jedwali halina cycle_id - swali linapitia mzunguko hadi
            // kitengo chake (angalia WaterQualityService).
            assertThat(graphql(workerToken,
                    "query { waterQualityLogs(cycleId: " + cycleA + ") { logId } }")
                    .path("data").path("waterQualityLogs")).hasSize(1);
        }

        @Test
        @DisplayName("logDate ikiachwa wazi inatumia leo")
        void defaultsToToday() {
            JsonNode created = graphql(workerToken, logMutation(unitA, "ph: 7.0"));

            assertThat(created.path("data").path("logWaterQuality").path("logId").asText()).isNotBlank();
            JsonNode listed = graphql(workerToken, "query { waterQualityLogs { logDate } }")
                    .path("data").path("waterQualityLogs");
            assertThat(listed.get(0).path("logDate").asText())
                    .isEqualTo(java.time.LocalDate.now().toString());
        }
    }

    @Nested
    @DisplayName("D-1: kuvuja kati ya mashamba")
    class CrossTenant {

        @Test
        @DisplayName("orodha ya shamba jingine haionekani kabisa")
        void listIsScopedToTheCallersFarm() {
            graphql(workerToken, logMutation(unitA, "ph: 7.0"));

            // Mfanyakazi wa shamba B: hana kipimo, ilhali kipo kwenye
            // database. Tupu, si kukataliwa - hii ni orodha ya shamba lake.
            assertThat(graphql(workerBToken, "query { waterQualityLogs { logId } }")
                    .path("data").path("waterQualityLogs")).isEmpty();
        }

        @Test
        @DisplayName("kusoma kitengo cha shamba jingine ni FORBIDDEN")
        void readingAnotherFarmsUnitIsForbidden() {
            JsonNode response = graphql(workerBToken,
                    "query { waterQualityLogs(unitId: " + unitA + ") { logId } }");

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).isEqualTo("Huruhusiwi kufikia shamba hili.");
        }

        @Test
        @DisplayName("kuandika kwenye kitengo cha shamba jingine ni FORBIDDEN")
        void writingToAnotherFarmsUnitIsForbidden() {
            JsonNode response = graphql(workerBToken, logMutation(unitA, "ph: 7.0"));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            // Na hakuna kilichoandikwa popote.
            assertThat(graphql(workerToken, "query { waterQualityLogs { logId } }")
                    .path("data").path("waterQualityLogs")).isEmpty();
        }

        @Test
        @DisplayName("upande wa pili pia: shamba A kwenda B ni FORBIDDEN")
        void theOtherDirectionIsBlockedToo() {
            // Pande MBILI kwa makusudi: ukaguzi ulioandikwa upande mmoja
            // pekee ndio hasa ulikuwa umeacha D-1 wazi kwenye CycleService.
            assertThat(graphqlErrorCode(graphql(workerToken, logMutation(unitB, "ph: 7.0"))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken,
                    "query { waterQualityLogs(unitId: " + unitB + ") { logId } }")))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("mzunguko wa shamba jingine pia umefungwa")
        void anotherFarmsCycleIsBlocked() {
            assertThat(graphqlErrorCode(graphql(workerBToken,
                    "query { waterQualityLogs(cycleId: " + cycleA + ") { logId } }")))
                    .isEqualTo("FORBIDDEN");
        }
    }

    @Nested
    @DisplayName("lango la ruhusa")
    class Permissions {

        @Test
        @DisplayName("VIEWER hawezi kurekodi - hana log_water_quality")
        void viewerCannotLog() {
            JsonNode response = graphql(viewerToken, logMutation(unitA, "ph: 7.0"));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("log_water_quality");
        }

        @Test
        @DisplayName("VIEWER anaweza kusoma - ana view_dashboard")
        void viewerCanRead() {
            graphql(workerToken, logMutation(unitA, "ph: 7.0"));

            JsonNode response = graphql(viewerToken, "query { waterQualityLogs { logId } }");
            assertThat(graphqlErrorCode(response)).isNull();
            assertThat(response.path("data").path("waterQualityLogs")).hasSize(1);
        }

        @Test
        @DisplayName("asiye na role hawezi hata kusoma - lango ni ruhusa, si uanachama")
        void noRoleCannotEvenRead() {
            JsonNode response = graphql(noroleToken, "query { waterQualityLogs { logId } }");

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("view_dashboard");
        }

        @Test
        @DisplayName("WORKER anafaulu ombi lile lile ambalo VIEWER amekataliwa")
        void workerSucceedsWhereViewerFailed() {
            assertThat(graphqlErrorCode(graphql(workerToken, logMutation(unitA, "ph: 7.0")))).isNull();
        }
    }

    @Nested
    @DisplayName("kipimo kibaya SI kosa")
    class OutOfRangeIsData {

        @Test
        @DisplayName("maji yanayoua yanahifadhiwa: DO 0.8, pH 4.2, amonia 0.9, 34.5C")
        void lethalReadingSaves() {
            JsonNode created = graphql(workerToken, logMutation(unitA,
                    "ph: 4.2, temperature: 34.5, oxygen: 0.8, ammonia: 0.9, "
                            + "notes: \"Samaki wanaelea juu\""));
            JsonNode log = created.path("data").path("logWaterQuality");

            assertThat(graphqlErrorCode(created)).isNull();
            assertThat(log.path("ph").asDouble()).isEqualTo(4.2);
            assertThat(log.path("oxygen").asDouble()).isEqualTo(0.8);
            assertThat(log.path("ammonia").asDouble()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("oksijeni sifuri kamili - kutoweka kwa hewa - kunahifadhiwa")
        void zeroOxygenSaves() {
            JsonNode created = graphql(workerToken, logMutation(unitA, "oxygen: 0, ph: 14.0"));

            assertThat(graphqlErrorCode(created)).isNull();
            assertThat(created.path("data").path("logWaterQuality").path("oxygen").asDouble()).isZero();
        }

        @Test
        @DisplayName("amonia 0.02 na 0.25 zinabaki tofauti - ndiyo sababu ya numeric(4,2)")
        void ammoniaKeepsTwoDecimals() {
            graphql(workerToken, logMutation(unitA, "ammonia: 0.02"));
            graphql(workerToken, logMutation(unitA, "ammonia: 0.25"));

            JsonNode listed = graphql(workerToken, "query { waterQualityLogs { ammonia } }")
                    .path("data").path("waterQualityLogs");
            java.util.List<Double> values = new java.util.ArrayList<>();
            listed.forEach(node -> values.add(node.path("ammonia").asDouble()));

            // Kwa desimali moja hizi mbili zingekuwa kipimo kile kile.
            assertThat(values).containsExactlyInAnyOrder(0.02, 0.25);
        }

        @Test
        @DisplayName("kipimo cha sehemu (pH pekee) kinahifadhiwa, nyingine null")
        void partialReadingSaves() {
            JsonNode log = graphql(workerToken, logMutation(unitA, "ph: 6.1"))
                    .path("data").path("logWaterQuality");

            assertThat(log.path("ph").asDouble()).isEqualTo(6.1);
            assertThat(log.path("temperature").isNull()).isTrue();
            assertThat(log.path("oxygen").isNull()).isTrue();
            assertThat(log.path("ammonia").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("kisichowezekana kimuundo kinakataliwa")
    class StructuralRejects {

        @Test
        @DisplayName("pH 15 - nje ya mizani")
        void phAboveScale() {
            JsonNode response = graphql(workerToken, logMutation(unitA, "ph: 15"));
            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).isEqualTo("pH lazima iwe kati ya 0 na 14.");
        }

        @Test
        @DisplayName("pH -1 - nje ya mizani upande wa pili")
        void phBelowScale() {
            assertThat(graphqlErrorCode(graphql(workerToken, logMutation(unitA, "ph: -1"))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("oksijeni hasi si kipimo")
        void negativeOxygen() {
            JsonNode response = graphql(workerToken, logMutation(unitA, "oxygen: -3.5"));
            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).contains("Oksijeni").contains("hasi");
        }

        @Test
        @DisplayName("amonia hasi si kipimo")
        void negativeAmmonia() {
            JsonNode response = graphql(workerToken, logMutation(unitA, "ammonia: -1"));
            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).contains("Amonia");
        }

        @Test
        @DisplayName("thamani kubwa kuliko safu inavyoweza kubeba")
        void beyondColumnCapacity() {
            assertThat(graphqlErrorCode(graphql(workerToken, logMutation(unitA, "temperature: 5000"))))
                    .isEqualTo("VALIDATION_ERROR");
            // numeric(4,2) ya amonia ina ukomo mdogo zaidi kuliko nyingine.
            assertThat(graphqlErrorCode(graphql(workerToken, logMutation(unitA, "ammonia: 150"))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("tarehe isiyosomeka")
        void unparseableDate() {
            JsonNode response = graphql(workerToken, logMutation(unitA, "logDate: \"01-09-2026\", ph: 7"));
            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).contains("YYYY-MM-DD");
        }

        @Test
        @DisplayName("kitengo kisichojulikana")
        void unknownUnit() {
            JsonNode response = graphql(workerToken, logMutation(999999, "ph: 7"));
            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(response)).isEqualTo("Kitengo hakijulikani");
        }

        @Test
        @DisplayName("hakuna kilichoandikwa baada ya kukataliwa kokote")
        void nothingIsPersistedOnReject() {
            graphql(workerToken, logMutation(unitA, "ph: 15"));
            graphql(workerToken, logMutation(unitA, "oxygen: -3.5"));
            graphql(workerToken, logMutation(999999, "ph: 7"));

            assertThat(graphql(workerToken, "query { waterQualityLogs { logId } }")
                    .path("data").path("waterQualityLogs")).isEmpty();
        }
    }
}
