package com.samaki.farm.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KUNDI C - Chakula.
 *
 * Module hii ina kitu ambacho nyingine hazina: LEJA INAYOJIANDIKA.
 * Mteja haandiki feed_stock_movements kamwe - kununua kunazalisha IN,
 * kulisha kunazalisha OUT, ndani ya transaction ile ile. Hesabu hiyo
 * ndiyo inayojibu "kuna chakula kiasi gani stoo", hivyo ikikosea
 * kimyakimya, shamba linapanga kwa namba isiyo ya kweli.
 */
@DisplayName("C - Chakula")
class FeedRegressionTest extends IntegrationTest {

    private String purchase(double kg, double unitCost) {
        return "mutation { recordFeedPurchase(input: {purchaseDate: \"2026-09-01\", "
                + "feedType: \"PELLET\", quantityKg: " + kg + ", unitCost: " + unitCost
                + ", supplier: \"Duka\"}) { purchaseId quantityKg unitCost totalCost } }";
    }

    private String feeding(double kg) {
        return "mutation { logFeeding(input: {cycleId: " + cycleA + ", quantityKg: " + kg
                + ", feedType: \"PELLET\"}) { logId quantityKg recordedByName } }";
    }

    private double balance(String token) {
        return graphql(token, "query { feedStockBalance }").path("data").path("feedStockBalance").asDouble();
    }

    @Nested
    @DisplayName("hesabu ya leja")
    class LedgerMath {

        @Test
        @DisplayName("stoo huanza sifuri")
        void startsEmpty() {
            assertThat(balance(adminToken)).isZero();
        }

        @Test
        @DisplayName("kununua kunaongeza stoo na kuzalisha movement ya IN")
        void purchaseAddsStock() {
            assertThat(graphqlErrorCode(graphql(adminToken, purchase(50, 1200)))).isNull();

            assertThat(balance(adminToken)).isEqualTo(50.0);

            JsonNode movements = graphql(adminToken,
                    "query { feedStockMovements { direction quantityKg referencePurchaseId } }")
                    .path("data").path("feedStockMovements");
            assertThat(movements).hasSize(1);
            assertThat(movements.get(0).path("direction").asText()).isEqualTo("IN");
            assertThat(movements.get(0).path("quantityKg").asDouble()).isEqualTo(50.0);
            // Leja inaunganishwa na ununuzi uliyoizalisha.
            assertThat(movements.get(0).path("referencePurchaseId").isNull()).isFalse();
        }

        @Test
        @DisplayName("kulisha kunapunguza stoo na kuzalisha movement ya OUT")
        void feedingRemovesStock() {
            graphql(adminToken, purchase(50, 1200));

            assertThat(graphqlErrorCode(graphql(workerToken, feeding(12.5)))).isNull();
            assertThat(balance(adminToken)).isEqualTo(37.5);

            JsonNode movements = graphql(adminToken,
                    "query { feedStockMovements { direction quantityKg referenceFeedingLogId } }")
                    .path("data").path("feedStockMovements");
            assertThat(movements).hasSize(2);
            assertThat(movements).anySatisfy(node -> {
                assertThat(node.path("direction").asText()).isEqualTo("OUT");
                assertThat(node.path("quantityKg").asDouble()).isEqualTo(12.5);
                assertThat(node.path("referenceFeedingLogId").isNull()).isFalse();
            });
        }

        @Test
        @DisplayName("manunuzi na ulishaji kadhaa vinajumlishwa kwa usahihi")
        void balanceIsTheRunningSum() {
            graphql(adminToken, purchase(100, 1000));
            graphql(adminToken, purchase(25.5, 1100));
            graphql(workerToken, feeding(30));
            graphql(workerToken, feeding(0.5));

            // 100 + 25.5 - 30 - 0.5
            assertThat(balance(adminToken)).isEqualTo(95.0);
            assertThat(graphql(adminToken, "query { feedStockMovements { direction } }")
                    .path("data").path("feedStockMovements")).hasSize(4);
        }

        @Test
        @DisplayName("stoo inaweza kwenda hasi - leja inaripoti, haihukumu")
        void balanceMayGoNegative() {
            // Hakuna ununuzi wowote, lakini kulisha kumetokea: hii ni hali
            // halisi ya shambani (chakula kilichokuwepo hakikurekodiwa),
            // na leja inaionyesha badala ya kuikataa.
            graphql(workerToken, feeding(10));

            assertThat(balance(adminToken)).isEqualTo(-10.0);
        }

        @Test
        @DisplayName("totalCost inakokotolewa na database, si na mteja")
        void totalCostIsComputed() {
            JsonNode created = graphql(adminToken, purchase(50, 1200))
                    .path("data").path("recordFeedPurchase");

            // GENERATED ALWAYS kwenye schema - haiwezi kutumwa na mteja.
            assertThat(created.path("totalCost").asDouble()).isEqualTo(60000.0);
        }
    }

    @Nested
    @DisplayName("lango la ruhusa")
    class Permissions {

        @Test
        @DisplayName("WORKER hawezi kununua - hana manage_feed_stock")
        void workerCannotPurchase() {
            JsonNode response = graphql(workerToken, purchase(50, 1200));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("manage_feed_stock");
        }

        @Test
        @DisplayName("WORKER anaweza kulisha - ana log_feeding")
        void workerCanFeed() {
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(5)))).isNull();
        }

        @Test
        @DisplayName("VIEWER hawezi kulisha wala kununua")
        void viewerCanDoNeither() {
            assertThat(graphqlErrorCode(graphql(viewerToken, feeding(5)))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken, purchase(10, 100)))).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("asiye na role hawezi hata kusoma salio")
        void noRoleCannotRead() {
            JsonNode response = graphql(noroleToken, "query { feedStockBalance }");

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).contains("view_dashboard");
        }
    }

    @Nested
    @DisplayName("D-1: kufungwa kwa shamba")
    class FarmScoping {

        @Test
        @DisplayName("salio la shamba jingine halionekani")
        void balanceIsPerFarm() {
            graphql(adminToken, purchase(80, 1000));

            // Shamba B halijanunua chochote, na halioni chochote cha A.
            assertThat(balance(workerBToken)).isZero();
        }

        @Test
        @DisplayName("kulisha mzunguko wa shamba jingine ni FORBIDDEN")
        void feedingAnotherFarmsCycleIsForbidden() {
            JsonNode response = graphql(workerBToken, feeding(5));

            assertThat(graphqlErrorCode(response)).isEqualTo("FORBIDDEN");
            assertThat(graphqlMessage(response)).isEqualTo("Huruhusiwi kufikia shamba hili.");
        }

        @Test
        @DisplayName("leja ya shamba jingine haionekani")
        void movementsAreScoped() {
            graphql(adminToken, purchase(80, 1000));
            graphql(workerToken, feeding(10));

            assertThat(graphql(workerBToken, "query { feedStockMovements { direction } }")
                    .path("data").path("feedStockMovements")).isEmpty();
            assertThat(graphql(workerBToken, "query { feedingLogs { logId } }")
                    .path("data").path("feedingLogs")).isEmpty();
        }
    }

    @Nested
    @DisplayName("kukataliwa kimuundo")
    class Validation {

        @Test
        @DisplayName("kiasi sifuri au hasi kinakataliwa kwa VALIDATION_ERROR")
        void nonPositiveQuantityIsRejected() {
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(0))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(workerToken, feeding(-5))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(adminToken, purchase(-1, 1000))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("mzunguko usiojulikana")
        void unknownCycle() {
            JsonNode response = graphql(workerToken,
                    "mutation { logFeeding(input: {cycleId: 999999, quantityKg: 5}) { logId } }");

            assertThat(graphqlErrorCode(response)).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("ombi lililokataliwa haliachi alama kwenye leja")
        void rejectedRequestsLeaveNoLedgerEntry() {
            graphql(workerToken, feeding(0));
            graphql(adminToken, purchase(-1, 1000));

            assertThat(balance(adminToken)).isZero();
            assertThat(graphql(adminToken, "query { feedStockMovements { direction } }")
                    .path("data").path("feedStockMovements")).isEmpty();
        }
    }
}
